package com.jngkzbird.arknights_angelina_pet.engine

import com.jngkzbird.arknights_angelina_pet.spine38.RenderTransform
import com.jngkzbird.arknights_angelina_pet.spine38.Skeleton
import com.jngkzbird.arknights_angelina_pet.spine38.SkeletonData
import com.jngkzbird.arknights_angelina_pet.spine38.apply_animation

/**
 * 布局精算 — 直译鸿蒙版 PetPage.PetModel/StateLayout（终局架构）：
 * 每模型固定画布（缩放按 setup、画布按用到的动画并集∪setup、锚点=身体轴/底边），
 * 跨模型归一化（全局统一画布 maxW×maxH，切换零拉伸），坐下/睡觉特判按状态居中。
 */
object PetLayout {
    const val RW = 300.0 // 参考画布宽（模型空间基准）
    const val RH = 480.0
    const val MARGIN = 14.0
}

class ModelLayout {
    // 画布（layout 空间，缩放后单位）——finalize 后为全局归一化值
    var w = 0.0
    var h = 0.0
    var scale = 1.0
    var tx = 0.0
    var ty = 0.0
    // 身体对称轴（根骨骼 setup world_x）
    var anchorX = 0.0
    // setup 底边距画布底部（含跨模型 bottomExtra 归一化）
    var charBottomPad = 0.0
    // setup 垂直中心距画布底部（拖拽垂直参考）
    var charCenterPad = 0.0
    var charHalfH = 0.0
    var setupMinY = 0.0
    var setupH = 0.0
    var bottomExtra = 0.0
    // 并集范围（模型空间，钳制特判变换用）
    var extMinX = 0.0
    var extMinY = 0.0
    var extMaxX = 0.0
    var extMaxY = 0.0
    // 特判动画（坐下/睡觉）：按状态居中变换
    val stateTransforms = HashMap<String, RenderTransform>()
}

class LayoutSpec(
    val baseAnim: String,
    val interactAnim: String?,
    val comboAnims: List<String>,
    val menuAnims: List<String>,
    val centeredAnims: List<String>,
    val vSlack: Double,
    val extraAnims: List<String>
)

/** 采样动画集合的水平并集（相对身体轴） */
private fun sampleX(sk: Skeleton, anims: List<String>, setup: DoubleArray, rootX: Double): DoubleArray {
    var mn = setup[0] - rootX
    var mx = setup[2] - rootX
    for (name in anims) {
        val anim = sk.data.find_animation(name) ?: continue
        val n = if (anim.duration > 0) 8 else 1
        for (s in 0 until n) {
            sk.set_to_setup_pose()
            apply_animation(anim, sk, anim.duration * s / n, true, 1.0)
            sk.update_world_transform()
            val bb = sk.get_bounds()
            mn = minOf(mn, bb[0] - rootX)
            mx = maxOf(mx, bb[0] + bb[2] - rootX)
        }
    }
    return doubleArrayOf(mn, mx)
}

/** 计算单模型布局（照鸿蒙 PetModel.computeLayout） */
fun computeModelLayout(sd: SkeletonData, spec: LayoutSpec): ModelLayout {
    val layout = ModelLayout()
    val sk = Skeleton(sd)
    sk.set_to_setup_pose()
    sk.update_world_transform()
    val b = sk.get_bounds()
    val setup = doubleArrayOf(b[0], b[1], b[0] + b[2], b[1] + b[3])
    val scale = minOf(
        (PetLayout.RW - PetLayout.MARGIN * 2) / b[2],
        (PetLayout.RH - PetLayout.MARGIN * 2) / b[3]
    )
    // 身体对称轴：根骨骼（无父骨骼）的 setup world_x；退回包围盒中心
    var rootX = (setup[0] + setup[2]) / 2
    for (bone in sk.bones) {
        if (bone.parent == null) {
            rootX = bone.world_x
            break
        }
    }
    layout.anchorX = rootX
    var minX = setup[0] - rootX
    var minY = setup[1]
    var maxX = setup[2] - rootX
    var maxY = setup[3]
    var wMinX = setup[0]
    var wMaxX = setup[2]
    val used = ArrayList<String>()
    used.add(spec.baseAnim)
    spec.interactAnim?.let { used.add(it) }
    used.addAll(spec.comboAnims)
    if (sd.find_animation("Start") != null) {
        used.add("Start")
    }
    used.addAll(spec.menuAnims)
    used.addAll(spec.extraAnims)
    for (name in used) {
        val anim = sd.find_animation(name) ?: continue
        val n = if (anim.duration > 0) 8 else 1
        for (s in 0 until n) {
            sk.set_to_setup_pose()
            apply_animation(anim, sk, anim.duration * s / n, true, 1.0)
            sk.update_world_transform()
            val bb = sk.get_bounds()
            minX = minOf(minX, bb[0] - rootX)
            minY = minOf(minY, bb[1])
            maxX = maxOf(maxX, bb[0] + bb[2] - rootX)
            maxY = maxOf(maxY, bb[1] + bb[3])
            wMinX = minOf(wMinX, bb[0])
            wMaxX = maxOf(wMaxX, bb[0] + bb[2])
        }
    }
    layout.w = Math.ceil((maxX - minX) * scale) + PetLayout.MARGIN * 2
    layout.h = Math.ceil((maxY - minY) * scale) + PetLayout.MARGIN * 2 + spec.vSlack
    layout.scale = scale
    layout.tx = layout.w / 2 - rootX * scale
    layout.bottomExtra = maxOf(0.0, setup[1] - minY) * scale
    layout.setupMinY = setup[1]
    layout.setupH = setup[3] - setup[1]
    layout.ty = (PetLayout.MARGIN + layout.bottomExtra) - setup[1] * scale
    layout.charHalfH = ((setup[3] - setup[1]) / 2) * scale
    layout.extMinX = wMinX
    layout.extMinY = minY
    layout.extMaxX = wMaxX
    layout.extMaxY = maxY
    // 特判动画包围盒 + 居中变换（finalize 时计算）
    for (name in spec.centeredAnims) {
        val anim = sd.find_animation(name) ?: continue
        val sb = doubleArrayOf(1e9, 1e9, -1e9, -1e9)
        val n = if (anim.duration > 0) 8 else 1
        for (s in 0 until n) {
            sk.set_to_setup_pose()
            apply_animation(anim, sk, anim.duration * s / n, true, 1.0)
            sk.update_world_transform()
            val bb = sk.get_bounds()
            sb[0] = minOf(sb[0], bb[0])
            sb[1] = minOf(sb[1], bb[1])
            sb[2] = maxOf(sb[2], bb[0] + bb[2])
            sb[3] = maxOf(sb[3], bb[1] + bb[3])
        }
        layout.stateTransforms[name] = RenderTransform() // 占位，finalize 填充
        // 临时存包围盒到 transform 的 tx/ty 字段（finalize 读取后覆盖）
        layout.stateTransforms[name]!!.tx = sb[0]
        layout.stateTransforms[name]!!.ty = sb[1]
        layout.stateTransforms[name]!!.scale = sb[2]
        layout.stateTransforms[name]!!.scaleX = sb[3]
    }
    return layout
}

/** 跨模型归一化：统一画布高度/底部扩展，并计算特判动画的居中变换（照 finalize） */
fun finalizeLayouts(layouts: List<ModelLayout>) {
    var maxBE = 0.0
    var maxW = 0.0
    var maxH = 0.0
    for (m in layouts) {
        maxBE = maxOf(maxBE, m.bottomExtra)
        maxW = maxOf(maxW, m.w)
        maxH = maxOf(maxH, m.h)
    }
    for (m in layouts) {
        val s = m.scale
        m.w = maxW
        m.h = maxH
        // 身体轴固定在画布中心
        m.tx = maxW / 2 - m.anchorX * s
        m.ty = (PetLayout.MARGIN + maxBE) - m.setupMinY * s
        m.charCenterPad = PetLayout.MARGIN + maxBE + m.setupH / 2 * s
        m.charBottomPad = PetLayout.MARGIN + maxBE
        for ((name, holder) in m.stateTransforms) {
            // holder 临时字段：tx=sb[0] ty=sb[1] scale=sb[2] scaleX=sb[3]
            val sb = doubleArrayOf(holder.tx, holder.ty, holder.scale, holder.scaleX)
            val t = RenderTransform()
            t.scale = s
            // 身体轴水平固定（站立/坐下/睡觉衔接零位移）；垂直按状态视觉中心对齐
            t.tx = maxW / 2 - m.anchorX * s
            t.ty = m.charCenterPad - ((sb[1] + sb[3]) / 2) * s
            // 钳制：并集内容保持在画布内
            val txMin = PetLayout.MARGIN - m.extMinX * s
            val txMax = maxW - PetLayout.MARGIN - m.extMaxX * s
            if (t.tx < txMin) t.tx = txMin
            if (t.tx > txMax) t.tx = txMax
            val tyMin = PetLayout.MARGIN - m.extMinY * s
            val tyMax = maxH - PetLayout.MARGIN - m.extMaxY * s
            if (t.ty < tyMin) t.ty = tyMin
            if (t.ty > tyMax) t.ty = tyMax
            m.stateTransforms[name] = t
        }
    }
}
