package com.jngkzbird.arknights_angelina_pet.spine38

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Spine 3.8 三角形收集器 — 移植自 spine38/renderer.ets（collect_triangles + polygon_mask）。
 * 架构定论：CPU 收集（vp 坐标三角形 + 颜色 + 混合 + 裁剪段标记）→ GPU 光栅化（GLES3 着色器掩码丢弃）。
 */

class RenderTransform {
    var scale: Double = 1.0 // y 轴缩放（恒正）
    var scaleX: Double = Double.NaN // x 轴缩放（NaN=跟随 scale；镜像时取负，只翻 x 不翻 y）
    var tx: Double = 0.0
    var ty: Double = 0.0

    fun sx(): Double = if (scaleX.isNaN()) scale else scaleX
}

class TriBatch {
    // 每三角 12 个：x0,y0,x1,y1,x2,y2,u0,v0,u1,v1,u2,v2（帧空间，y 已翻转）
    var tris: DoubleArray = DoubleArray(0)
    // 每三角 5 个：r,g,b,a,blend
    var colors: DoubleArray = DoubleArray(0)
    // 每三角：裁剪段 id，-1=普通
    var flags: IntArray = IntArray(0)
    // 各裁剪段多边形顶点拼接（x,y 交替，帧空间）
    var segFlat: DoubleArray = DoubleArray(0)
    // 各段顶点数
    var segSizes: IntArray = IntArray(0)
    var triCount: Int = 0
    var segCount: Int = 0
}

class ClipMask(val w: Int, val h: Int) {
    val mask: ByteArray = ByteArray(w * h)
    var x0: Int = 0
    var y0: Int = 0
    var x1: Int = 0
    var y1: Int = 0
}

// 多边形填充掩码（偶数奇数规则），bbox 裁剪。pts 为帧空间（y 已翻转）。
fun polygon_mask(pts: DoubleArray, off: Int, n: Int, w: Int, h: Int, cm: ClipMask) {
    cm.x0 = 0; cm.y0 = 0; cm.x1 = 0; cm.y1 = 0
    if (n < 3) {
        return
    }
    var minX = pts[off]
    var maxX = pts[off]
    var minY = pts[off + 1]
    var maxY = pts[off + 1]
    var i = off + 2
    while (i < off + n * 2) {
        if (pts[i] < minX) minX = pts[i]
        if (pts[i] > maxX) maxX = pts[i]
        if (pts[i + 1] < minY) minY = pts[i + 1]
        if (pts[i + 1] > maxY) maxY = pts[i + 1]
        i += 2
    }
    var x0 = floor(minX).toInt()
    if (x0 < 0) x0 = 0
    var x1 = ceil(maxX).toInt()
    if (x1 > w) x1 = w
    var y0 = floor(minY).toInt()
    if (y0 < 0) y0 = 0
    var y1 = ceil(maxY).toInt()
    if (y1 > h) y1 = h
    if (x1 <= x0 || y1 <= y0) {
        return
    }
    cm.x0 = x0; cm.y0 = y0; cm.x1 = x1; cm.y1 = y1
    // 只清零包围盒区域
    for (y in y0 until y1) {
        java.util.Arrays.fill(cm.mask, y * w + x0, y * w + x1, 0)
    }
    for (y in y0 until y1) {
        val py = y + 0.5
        for (x in x0 until x1) {
            val px = x + 0.5
            var inside = false
            for (p in 0 until n) {
                val x1v = pts[off + p * 2]
                val y1v = pts[off + p * 2 + 1]
                val j = (p + 1) % n
                val x2v = pts[off + j * 2]
                val y2v = pts[off + j * 2 + 1]
                val cond = (y1v > py) != (y2v > py)
                if (cond) {
                    var denom = y2v - y1v
                    if (denom == 0.0) denom = 1e-9
                    val xint = (x2v - x1v) * (py - y1v) / denom + x1v
                    if (px < xint) inside = !inside
                }
            }
            if (inside) cm.mask[y * w + x] = -1 // 255 语义
        }
    }
}

// 每帧复用的收集缓冲（杜绝每帧分配 → GC 停顿）
private var triBuf = DoubleArray(4096 * 12)
private var colorBuf = DoubleArray(4096 * 5)
private var flagBuf = IntArray(4096)
private var segBuf = DoubleArray(256)
private var segSizeBuf = IntArray(16)
private val batchReuse = TriBatch()
// 跨线程读（渲染线程写、UI 线程读），必须 volatile 保证可见性
@Volatile
var lastTriCount = 0 // 上一帧有效三角形数（像素级命中测试用）

// 像素级命中测试：点 (x,y)（vp，与三角形同坐标系）是否落在上一帧任一三角形内
fun hit_test_point(x: Double, y: Double): Boolean {
    val n = lastTriCount
    var t = 0
    while (t < n) {
        val b = t * 12
        val x0 = triBuf[b]
        val y0 = triBuf[b + 1]
        val x1 = triBuf[b + 2]
        val y1 = triBuf[b + 3]
        val x2 = triBuf[b + 4]
        val y2 = triBuf[b + 5]
        val d0 = (x - x0) * (y1 - y0) - (y - y0) * (x1 - x0)
        val d1 = (x - x1) * (y2 - y1) - (y - y1) * (x2 - x1)
        val d2 = (x - x2) * (y0 - y2) - (y - y2) * (x0 - x2)
        val neg = d0 < 0 || d1 < 0 || d2 < 0
        val pos = d0 > 0 || d1 > 0 || d2 > 0
        if (!(neg && pos)) {
            return true
        }
        t++
    }
    return false
}

// 按绘制顺序收集整帧三角形（含裁剪段标记）。写入共享缓冲，零每帧分配。
fun collect_triangles(skeleton: Skeleton, w: Double, h: Double, transform: RenderTransform): TriBatch {
    val scale = transform.scale
    val sx = transform.sx()
    val tx = transform.tx
    val ty = transform.ty
    var triIdx = 0
    var colIdx = 0
    var flgIdx = 0
    var clipPolys: List<DoubleArray>? = null
    var clipEndIndex = -1

    fun emit(
        x0: Double, y0: Double, x1: Double, y1: Double, x2: Double, y2: Double,
        u0: Double, v0: Double, u1: Double, v1: Double, u2: Double, v2: Double,
        cr: Double, cg: Double, cb: Double, ca: Double, blend: Int, flag: Int
    ) {
        if (triIdx + 12 > triBuf.size) {
            triBuf = triBuf.copyOf(triBuf.size * 2)
        }
        if (colIdx + 5 > colorBuf.size) {
            colorBuf = colorBuf.copyOf(colorBuf.size * 2)
        }
        if (flgIdx + 1 > flagBuf.size) {
            flagBuf = flagBuf.copyOf(flagBuf.size * 2)
        }
        triBuf[triIdx] = x0; triBuf[triIdx + 1] = y0
        triBuf[triIdx + 2] = x1; triBuf[triIdx + 3] = y1
        triBuf[triIdx + 4] = x2; triBuf[triIdx + 5] = y2
        triBuf[triIdx + 6] = u0; triBuf[triIdx + 7] = v0
        triBuf[triIdx + 8] = u1; triBuf[triIdx + 9] = v1
        triBuf[triIdx + 10] = u2; triBuf[triIdx + 11] = v2
        triIdx += 12
        colorBuf[colIdx] = cr; colorBuf[colIdx + 1] = cg
        colorBuf[colIdx + 2] = cb; colorBuf[colIdx + 3] = ca
        colorBuf[colIdx + 4] = blend.toDouble()
        colIdx += 5
        flagBuf[flgIdx] = flag
        flgIdx += 1
    }

    for (slot in skeleton.draw_order) {
        val attachment = slot.attachment
        if (attachment == null) {
            if (clipPolys != null && slot.data.index == clipEndIndex) {
                clipPolys = null
                clipEndIndex = -1
            }
            continue
        }
        if (attachment is ClippingAttachment) {
            // 官方语义：裁剪附件世界顶点 → 凸分解多边形（骨架坐标）；已在裁剪中则忽略新裁剪
            if (clipPolys == null) {
                val verts = compute_mesh_vertices(
                    slot, attachment, 0,
                    floor(attachment.world_vertices_length / 2.0).toInt()
                )
                clipPolys = clipAttachmentPolys(verts)
                clipEndIndex = attachment.end_slot?.index ?: skeleton.slots.size
            }
            continue
        }
        var verts: DoubleArray
        var triangles: IntArray
        var uvs: DoubleArray
        var region: AtlasRegion?
        if (attachment is RegionAttachment) {
            verts = compute_region_vertices(slot, attachment)
            triangles = intArrayOf(0, 1, 2, 2, 3, 0)
            region = attachment.region
            val r = region!!
            // 角点顺序：BL, TL, TR, BR
            uvs = if (r.degrees == 90) {
                doubleArrayOf(r.u2, r.v2, r.u, r.v2, r.u, r.v, r.u2, r.v)
            } else {
                doubleArrayOf(r.u, r.v2, r.u, r.v, r.u2, r.v, r.u2, r.v2)
            }
        } else if (attachment is MeshAttachment) {
            verts = compute_mesh_vertices(
                slot, attachment, 0,
                floor(attachment.world_vertices_length / 2.0).toInt()
            )
            triangles = attachment.triangles!!
            uvs = attachment.uvs!!
            region = attachment.region
        } else {
            continue
        }
        if (region == null || region.page == null) {
            continue
        }
        val page = region.page as AtlasPage
        if (page.texture == null) {
            continue
        }
        val cr = slot.color.r
        val cg = slot.color.g
        val cb = slot.color.b
        val ca = slot.color.a
        val blend = slot.data.blend_mode
        if (clipPolys != null) {
            // 裁剪段：官方 S-H 逐三角形裁剪（骨架坐标）→ 扇形三角化 → 帧坐标输出
            var t = 0
            while (t < triangles.size) {
                val i0 = triangles[t] * 2
                val i1 = triangles[t + 1] * 2
                val i2 = triangles[t + 2] * 2
                val results = clipTriangleToPolys(
                    doubleArrayOf(verts[i0], verts[i0 + 1], verts[i1], verts[i1 + 1], verts[i2], verts[i2 + 1]),
                    doubleArrayOf(uvs[i0], uvs[i0 + 1], uvs[i1], uvs[i1 + 1], uvs[i2], uvs[i2 + 1]),
                    clipPolys!!
                )
                for (r in results) {
                    val pts = r.pts
                    val uvr = r.uvs
                    val m = pts.size / 2
                    if (m < 3) {
                        continue
                    }
                    val fx0 = pts[0] * sx + tx
                    val fy0 = h - (pts[1] * scale + ty)
                    var f2 = 1
                    while (f2 < m - 1) {
                        emit(
                            fx0, fy0,
                            pts[f2 * 2] * sx + tx, h - (pts[f2 * 2 + 1] * scale + ty),
                            pts[(f2 + 1) * 2] * sx + tx, h - (pts[(f2 + 1) * 2 + 1] * scale + ty),
                            uvr[0], uvr[1], uvr[f2 * 2], uvr[f2 * 2 + 1],
                            uvr[(f2 + 1) * 2], uvr[(f2 + 1) * 2 + 1],
                            cr, cg, cb, ca, blend, -1
                        )
                        f2 += 1
                    }
                }
                t += 3
            }
        } else {
            var t = 0
            while (t < triangles.size) {
                val i0 = triangles[t] * 2
                val i1 = triangles[t + 1] * 2
                val i2 = triangles[t + 2] * 2
                emit(
                    verts[i0] * sx + tx, h - (verts[i0 + 1] * scale + ty),
                    verts[i1] * sx + tx, h - (verts[i1 + 1] * scale + ty),
                    verts[i2] * sx + tx, h - (verts[i2 + 1] * scale + ty),
                    uvs[i0], uvs[i0 + 1], uvs[i1], uvs[i1 + 1], uvs[i2], uvs[i2 + 1],
                    cr, cg, cb, ca, blend, -1
                )
                t += 3
            }
        }
        // 裁剪结束：end_slot 本身也裁剪，渲染后结束裁剪
        if (clipPolys != null && slot.data.index == clipEndIndex) {
            clipPolys = null
            clipEndIndex = -1
        }
    }
    val batch = batchReuse
    batch.tris = triBuf
    batch.colors = colorBuf
    batch.flags = flagBuf
    batch.segFlat = segBuf
    batch.segSizes = segSizeBuf
    batch.triCount = flgIdx
    batch.segCount = 0 // 掩码管线已废弃：裁剪在收集阶段完成（官方 S-H 语义，2026-08-20）
    lastTriCount = flgIdx
    return batch
}
