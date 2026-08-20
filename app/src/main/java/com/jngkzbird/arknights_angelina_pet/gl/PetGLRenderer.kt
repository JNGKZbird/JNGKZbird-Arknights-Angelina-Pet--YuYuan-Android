package com.jngkzbird.arknights_angelina_pet.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import com.jngkzbird.arknights_angelina_pet.spine38.ClipMask
import com.jngkzbird.arknights_angelina_pet.spine38.TriBatch
import com.jngkzbird.arknights_angelina_pet.spine38.polygon_mask
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * GLES3 渲染后端 — 直译鸿蒙版 C++ 终局管线（raster.cpp RenderFrameGL）：
 * CPU 收集 vp 三角形 → 顶点缓冲 → 偶数奇数掩码纹理 + 着色器 discard → 预乘混合 → swap。
 * 实例绑定单渲染线程（EGL 上下文线程私有），跨 surface 不共享。
 */
class PetGLRenderer {
    companion object {
        private const val TAG = "PetGL"
        private const val EGL_OPENGL_ES3_BIT = 0x0040

        private const val VS_SRC = """#version 300 es
layout(location=0) in vec2 a_pos;    // vp 坐标（显示盒空间）
layout(location=1) in vec2 a_uv;
layout(location=2) in vec4 a_color;  // 槽位色 0-1（直通）
uniform vec2 u_viewport;             // (vpW, vpH)
out vec2 v_uv;
out vec4 v_color;
out vec2 v_maskUv;
void main() {
    vec2 ndc = vec2(a_pos.x / u_viewport.x * 2.0 - 1.0,
                    1.0 - a_pos.y / u_viewport.y * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
    v_uv = a_uv;
    v_color = a_color;
    v_maskUv = a_pos / u_viewport;
}
"""

        private const val FS_SRC = """#version 300 es
precision mediump float;
uniform sampler2D u_tex;
uniform sampler2D u_mask;   // RGBA8：R=裁剪段 id+1（0=无裁剪；CPU 偶数奇数掩码）
uniform int u_clipId;       // 当前运行的段 id+1（0=不裁剪）
in vec2 v_uv;
in vec4 v_color;
in vec2 v_maskUv;
out vec4 fragColor;
void main() {
    if (u_clipId == -2) {
        // 调试：掩码可视化（段 id / 3 显示为红色阶）
        float m = texture(u_mask, v_maskUv).r * 255.0;
        fragColor = vec4(m / 3.0, 0.0, 0.0, 1.0);
        return;
    }
    if (u_clipId > 0) {
        float m = texture(u_mask, v_maskUv).r * 255.0;
        if (m + 0.5 < float(u_clipId) || m - 0.5 > float(u_clipId)) {
            discard;
        }
    }
    vec4 t = texture(u_tex, v_uv);   // 预乘纹理（Bitmap ARGB_8888 本身即预乘，插值无黑边）
    vec3 straight = t.a > 0.0 ? t.rgb / t.a : vec3(0.0);
    vec3 c = straight * v_color.rgb;
    float a = t.a * v_color.a;
    fragColor = vec4(c * a, a);      // 预乘输出（表面合成语义）
}
"""
    }

    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var uViewportLoc = -1
    private var uClipIdLoc = -1
    private var uTexLoc = -1
    private var uMaskLoc = -1
    private var vbo = 0
    private var atlasTexId = 0
    private var maskTexId = 0
    private var glReady = false

    // 掩码纹理状态（vp 分辨率 RGBA8；R=段 id+1）
    private var maskPx: ByteArray = ByteArray(0)
    private var prevMasks: IntArray = IntArray(0)
    private var maskTexW = 0
    private var maskTexH = 0
    private var clipMask = ClipMask(1, 1)

    private val vtxBuf: FloatBuffer =
        ByteBuffer.allocateDirect(4096 * 24 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun compileShader(type: Int, src: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e(TAG, "shader fail: ${GLES30.glGetShaderInfoLog(sh)}")
            return 0
        }
        return sh
    }

    fun init(surface: Any): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay fail")
            return false
        }
        val maj = IntArray(1)
        val min = IntArray(1)
        if (!EGL14.eglInitialize(display, maj, 0, min, 0)) {
            Log.e(TAG, "eglInitialize fail err=${EGL14.eglGetError()}")
            return false
        }
        val cfgAttr = intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val cfgN = IntArray(1)
        if (!EGL14.eglChooseConfig(display, cfgAttr, 0, cfgs, 0, 1, cfgN, 0) || cfgN[0] == 0) {
            Log.e(TAG, "eglChooseConfig fail err=${EGL14.eglGetError()}")
            return false
        }
        val ctxAttr = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, cfgs[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext fail err=${EGL14.eglGetError()}")
            return false
        }
        this.surface = EGL14.eglCreateWindowSurface(display, cfgs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (this.surface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface fail err=${EGL14.eglGetError()}")
            return false
        }
        if (!EGL14.eglMakeCurrent(display, this.surface, this.surface, context)) {
            Log.e(TAG, "eglMakeCurrent fail err=${EGL14.eglGetError()}")
            return false
        }
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, VS_SRC)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, FS_SRC)
        if (vs == 0 || fs == 0) {
            return false
        }
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e(TAG, "link fail: ${GLES30.glGetProgramInfoLog(program)}")
            return false
        }
        uViewportLoc = GLES30.glGetUniformLocation(program, "u_viewport")
        uClipIdLoc = GLES30.glGetUniformLocation(program, "u_clipId")
        uTexLoc = GLES30.glGetUniformLocation(program, "u_tex")
        uMaskLoc = GLES30.glGetUniformLocation(program, "u_mask")
        GLES30.glUseProgram(program)
        GLES30.glUniform1i(uTexLoc, 0)
        GLES30.glUniform1i(uMaskLoc, 1)
        val bufs = IntArray(1)
        GLES30.glGenBuffers(1, bufs, 0)
        vbo = bufs[0]
        val texs = IntArray(2)
        GLES30.glGenTextures(2, texs, 0)
        atlasTexId = texs[0]
        maskTexId = texs[1]
        // 掩码纹理参数
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        // 图集颜色纹理参数
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTexId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        glReady = true
        return true
    }

    // 上传图集纹理（Bitmap ARGB_8888 解码即预乘，与鸿蒙 GL 上传语义一致）
    // 坑（MuMu 实证）：GLUtils.texImage2D 上传的纹理采样全零（FBO 读回有内容但着色器采不到）
    // → 绕过 GLUtils，copyPixelsToBuffer + glTexImage2D 直传
    // 上传图集纹理（Bitmap ARGB_8888 解码即预乘，与鸿蒙 GL 上传语义一致）
    fun uploadTexture(bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTexId)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        val err = GLES30.glGetError()
        if (err != 0) {
            Log.e(TAG, "atlas upload err=0x${Integer.toHexString(err)}")
        }
    }

    private fun setBlend(blend: Int) {
        when (blend) {
            0 -> GLES30.glBlendFuncSeparate(
                GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA,
                GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA
            )
            1 -> GLES30.glBlendFuncSeparate(
                GLES30.GL_ONE, GLES30.GL_ONE,
                GLES30.GL_ONE, GLES30.GL_ONE
            )
            2 -> GLES30.glBlendFuncSeparate(
                GLES30.GL_DST_COLOR, GLES30.GL_ONE_MINUS_SRC_ALPHA,
                GLES30.GL_ZERO, GLES30.GL_ONE
            )
            else -> GLES30.glBlendFuncSeparate(
                GLES30.GL_DST_ALPHA, GLES30.GL_ONE_MINUS_SRC_COLOR,
                GLES30.GL_ZERO, GLES30.GL_ONE
            )
        }
    }

    private fun drawRun(startVert: Int, vertCount: Int, blend: Int, clipId: Int) {
        if (vertCount <= 0) {
            return
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val stride = 8 * 4
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 2 * 4)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 4 * 4)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glUniform1i(uClipIdLoc, clipId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        setBlend(blend)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, startVert, vertCount)
    }

    // 上屏（GPU 版）：收集的 vp 三角形 → 顶点缓冲 → 掩码裁剪 + 混合运行 → swap
    // swap=false 时留给调用方在 swap 前读回像素（探针验证用）
    fun drawFrame(batch: TriBatch, bitmap: Bitmap, vpW: Double, vpH: Double, swap: Boolean = true) {
        if (!glReady || batch.triCount <= 0) {
            return
        }
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            return
        }
        // 表面真实尺寸每帧查询（init 时尺寸可能是互换的，用错值 = 角色横向压窄的根因）
        val sw = IntArray(1)
        val sh = IntArray(1)
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_WIDTH, sw, 0)
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_HEIGHT, sh, 0)
        GLES30.glViewport(0, 0, sw[0], sh[0])
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(program)
        GLES30.glUniform2f(uViewportLoc, vpW.toFloat(), vpH.toFloat())
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTexId)

        // ── 裁剪掩码：CPU 偶数奇数填充 → RGBA8 纹理 → 着色器丢弃 ──
        val vpw = (vpW + 0.5).toInt().coerceAtLeast(1)
        val vph = (vpH + 0.5).toInt().coerceAtLeast(1)
        // 必须按宽高判断重建：竖屏 1080×2400 ↔ 横屏 2400×1080 总字节数相同，
        // 只比总量会漏判 → 旧帧掩码坐标按新宽度计算索引 → 越界崩溃（真机旋转闪退根因）
        if (maskTexW != vpw || maskTexH != vph) {
            maskPx = ByteArray(vpw * vph * 4)
            prevMasks = IntArray(0)
            clipMask = ClipMask(vpw, vph)
        }
        val up = ArrayList<Int>(prevMasks.size)
        for (v in prevMasks) {
            up.add(v)
        }
        // 清旧区域
        var p = 0
        while (p < prevMasks.size) {
            val x0 = prevMasks[p]
            val y0 = prevMasks[p + 1]
            val x1 = prevMasks[p + 2]
            val y1 = prevMasks[p + 3]
            for (cy in y0 until y1) {
                java.util.Arrays.fill(maskPx, (cy * vpw + x0) * 4, (cy * vpw + x1) * 4, 0)
            }
            p += 4
        }
        prevMasks = IntArray(0)
        val cm = clipMask
        var poff = 0
        for (s in 0 until batch.segCount) {
            val n = batch.segSizes[s]
            polygon_mask(batch.segFlat, poff, n, vpw, vph, cm)
            poff += n * 2
            if (cm.x1 <= cm.x0 || cm.y1 <= cm.y0) {
                continue
            }
            val idv = (s + 1).toByte()
            for (cy in cm.y0 until cm.y1) {
                for (cx in cm.x0 until cm.x1) {
                    if (cm.mask[cy * vpw + cx].toInt() != 0) {
                        maskPx[(cy * vpw + cx) * 4] = idv
                    }
                }
            }
            prevMasks += intArrayOf(cm.x0, cm.y0, cm.x1, cm.y1)
            up.add(cm.x0)
            up.add(cm.y0)
            up.add(cm.x1)
            up.add(cm.y1)
        }
        // 掩码纹理分配 + 分块上传
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexId)
        if (maskTexW != vpw || maskTexH != vph) {
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, vpw, vph, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(maskPx)
            )
            val maskErr = GLES30.glGetError()
            if (maskErr != 0) {
                Log.e(TAG, "mask alloc err=0x${Integer.toHexString(maskErr)} size=${vpw}x${vph}")
            }
            maskTexW = vpw
            maskTexH = vph
        } else {
            var q = 0
            while (q < up.size) {
                val x0 = up[q]
                val y0 = up[q + 1]
                val x1 = up[q + 2]
                val y1 = up[q + 3]
                if (x1 > x0 && y1 > y0) {
                    GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
                    for (cy in y0 until y1) {
                        GLES30.glTexSubImage2D(
                            GLES30.GL_TEXTURE_2D, 0, x0, cy, x1 - x0, 1,
                            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
                            ByteBuffer.wrap(maskPx, (cy * vpw + x0) * 4, (x1 - x0) * 4)
                        )
                    }
                }
                q += 4
            }
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        // 顶点填充：全部三角形直传（裁剪交给着色器掩码丢弃）
        val need = batch.triCount * 24
        if (need > vtxBuf.capacity()) {
            Log.e(TAG, "vtx buffer overflow need=$need")
            return
        }
        vtxBuf.clear()
        vtxBuf.limit(need)
        for (t in 0 until batch.triCount) {
            val b = t * 12
            val c = t * 5
            val r = batch.colors[c].toFloat()
            val g = batch.colors[c + 1].toFloat()
            val bl = batch.colors[c + 2].toFloat()
            val a = batch.colors[c + 3].toFloat()
            vtxBuf.put(batch.tris[b].toFloat()); vtxBuf.put(batch.tris[b + 1].toFloat())
            vtxBuf.put(batch.tris[b + 6].toFloat()); vtxBuf.put(batch.tris[b + 7].toFloat())
            vtxBuf.put(r); vtxBuf.put(g); vtxBuf.put(bl); vtxBuf.put(a)
            vtxBuf.put(batch.tris[b + 2].toFloat()); vtxBuf.put(batch.tris[b + 3].toFloat())
            vtxBuf.put(batch.tris[b + 8].toFloat()); vtxBuf.put(batch.tris[b + 9].toFloat())
            vtxBuf.put(r); vtxBuf.put(g); vtxBuf.put(bl); vtxBuf.put(a)
            vtxBuf.put(batch.tris[b + 4].toFloat()); vtxBuf.put(batch.tris[b + 5].toFloat())
            vtxBuf.put(batch.tris[b + 10].toFloat()); vtxBuf.put(batch.tris[b + 11].toFloat())
            vtxBuf.put(r); vtxBuf.put(g); vtxBuf.put(bl); vtxBuf.put(a)
        }
        vtxBuf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, need * 4, vtxBuf, GLES30.GL_STREAM_DRAW)
        // 按（混合模式, 裁剪段）刷运行
        var runStart = 0
        var runBlend = -1
        var runFlag = -2
        for (t in 0 until batch.triCount) {
            val blend = batch.colors[t * 5 + 4].toInt()
            val flag = batch.flags[t]
            if (runBlend != blend || runFlag != flag) {
                if (runBlend >= 0) {
                    drawRun(runStart, t * 3 - runStart, runBlend, runFlag + 1)
                }
                runStart = t * 3
                runBlend = blend
                runFlag = flag
            }
        }
        if (runBlend >= 0) {
            drawRun(runStart, batch.triCount * 3 - runStart, runBlend, runFlag + 1)
        }
        if (swap) {
            EGL14.eglSwapBuffers(display, surface)
        }
    }

    fun swap() {
        if (glReady) {
            EGL14.eglSwapBuffers(display, surface)
        }
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, surface)
            }
            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglTerminate(display)
        }
        glReady = false
    }
}
