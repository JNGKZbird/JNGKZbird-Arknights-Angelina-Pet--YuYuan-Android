package com.jngkzbird.arknights_angelina_pet.spine38

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spine 3.8 姿态计算 — ported from spine38/skeleton.ets（spine-ts 3.8 Bone/Skeleton）。
 * 与 Python 同构：骨骼与约束按依赖序交错排入 update_cache（约束改父骨骼后子骨骼才更新）。
 */

private const val RAD_DEG = 180.0 / PI
private const val DEG_RAD = PI / 180.0

private fun cos_deg(d: Double): Double = cos(d * DEG_RAD)

private fun sin_deg(d: Double): Double = sin(d * DEG_RAD)

class Bone(val data: BoneData, val skeleton: Skeleton, val parent: Bone?) : Updatable {
    var x: Double
    var y: Double
    var rotation: Double
    var scale_x: Double
    var scale_y: Double
    var shear_x: Double
    var shear_y: Double
    // 已应用变换（约束系统使用）
    var ax: Double
    var ay: Double
    var arotation: Double
    var ascale_x: Double
    var ascale_y: Double
    var ashear_x: Double
    var ashear_y: Double
    var a: Double = 0.0
    var b: Double = 0.0
    var c: Double = 0.0
    var d: Double = 0.0
    var world_x: Double = 0.0
    var world_y: Double = 0.0
    var active: Boolean = true
    var applied_valid: Boolean = false
    val children: MutableList<Bone> = mutableListOf()
    var sorted: Boolean = false

    init {
        x = data.x
        y = data.y
        rotation = data.rotation
        scale_x = data.scale_x
        scale_y = data.scale_y
        shear_x = data.shear_x
        shear_y = data.shear_y
        ax = x
        ay = y
        arotation = rotation
        ascale_x = scale_x
        ascale_y = scale_y
        ashear_x = shear_x
        ashear_y = shear_y
    }

    fun update_world_transform() {
        _update_with(x, y, rotation, scale_x, scale_y, shear_x, shear_y)
    }

    override fun update() {
        update_world_transform()
    }

    // 从世界矩阵反推局部已应用变换（约束修改世界坐标后调用）
    fun update_applied_transform() {
        applied_valid = true
        val parent = this.parent
        if (parent == null) {
            ax = world_x
            ay = world_y
            arotation = atan2(c, a) * RAD_DEG
            ascale_x = sqrt(a * a + c * c)
            ascale_y = sqrt(b * b + d * d)
            ashear_x = 0.0
            ashear_y = atan2(a * b + c * d, a * d - b * c) * RAD_DEG
            return
        }
        val pa = parent.a
        val pb = parent.b
        val pc = parent.c
        val pd = parent.d
        val det = pa * pd - pb * pc
        if (abs(det) < 0.0001) {
            return
        }
        val pid = 1.0 / det
        val dx = world_x - parent.world_x
        val dy = world_y - parent.world_y
        ax = dx * pd * pid - dy * pb * pid
        ay = dy * pa * pid - dx * pc * pid
        val ia = pid * pd
        val id = pid * pa
        val ib = pid * pb
        val ic = pid * pc
        val ra = ia * a - ib * c
        val rb = ia * b - ib * d
        val rc = id * c - ic * a
        val rd = id * d - ic * b
        ashear_x = 0.0
        ascale_x = sqrt(ra * ra + rc * rc)
        if (ascale_x > 0.0001) {
            val det2 = ra * rd - rb * rc
            ascale_y = det2 / ascale_x
            ashear_y = atan2(ra * rb + rc * rd, det2) * RAD_DEG
            arotation = atan2(rc, ra) * RAD_DEG
        } else {
            ascale_x = 0.0
            ascale_y = sqrt(rb * rb + rd * rd)
            ashear_y = 0.0
            arotation = 90 - atan2(rd, rb) * RAD_DEG
        }
    }

    fun _update_with(
        x: Double, y: Double, rotation: Double, scale_x: Double, scale_y: Double,
        shear_x: Double, shear_y: Double
    ) {
        ax = x
        ay = y
        arotation = rotation
        ascale_x = scale_x
        ascale_y = scale_y
        ashear_x = shear_x
        ashear_y = shear_y
        applied_valid = true
        val parent = this.parent
        if (parent == null) {
            val skeleton = this.skeleton
            val rotation_y = rotation + 90 + shear_y
            val sx = skeleton.scale_x
            val sy = skeleton.scale_y
            a = cos_deg(rotation + shear_x) * scale_x * sx
            b = cos_deg(rotation_y) * scale_y * sx
            c = sin_deg(rotation + shear_x) * scale_x * sy
            d = sin_deg(rotation_y) * scale_y * sy
            world_x = x * sx + skeleton.x
            world_y = y * sy + skeleton.y
            return
        }
        val pa = parent.a
        val pb = parent.b
        val pc = parent.c
        val pd = parent.d
        world_x = pa * x + pb * y + parent.world_x
        world_y = pc * x + pd * y + parent.world_y
        when (data.transform_mode) {
            TRANSFORM_NORMAL -> {
                val rotation_y = rotation + 90 + shear_y
                val la = cos_deg(rotation + shear_x) * scale_x
                val lb = cos_deg(rotation_y) * scale_y
                val lc = sin_deg(rotation + shear_x) * scale_x
                val ld = sin_deg(rotation_y) * scale_y
                a = pa * la + pb * lc
                b = pa * lb + pb * ld
                c = pc * la + pd * lc
                d = pc * lb + pd * ld
            }

            TRANSFORM_ONLY_TRANSLATION -> {
                val rotation_y = rotation + 90 + shear_y
                a = cos_deg(rotation + shear_x) * scale_x
                b = cos_deg(rotation_y) * scale_y
                c = sin_deg(rotation + shear_x) * scale_x
                d = sin_deg(rotation_y) * scale_y
            }

            TRANSFORM_NO_ROTATION_OR_REFLECTION -> {
                var s = pa * pa + pc * pc
                var prx = 0.0
                var lpa = pa
                var lpc = pc
                var lpb = pb
                var lpd = pd
                if (s > 0.0001) {
                    s = abs(lpa * lpd - lpb * lpc) / s
                    lpa /= skeleton.scale_x
                    lpc /= skeleton.scale_y
                    lpb = lpc * s
                    lpd = lpa * s
                    prx = atan2(lpc, lpa) * RAD_DEG
                } else {
                    lpa = 0.0
                    lpc = 0.0
                    prx = 90 - atan2(lpd, lpb) * RAD_DEG
                }
                val rx = rotation + shear_x - prx
                val ry = rotation + shear_y - prx + 90
                val la = cos_deg(rx) * scale_x
                val lb = cos_deg(ry) * scale_y
                val lc = sin_deg(rx) * scale_x
                val ld = sin_deg(ry) * scale_y
                a = lpa * la - lpb * lc
                b = lpa * lb - lpb * ld
                c = lpc * la + lpd * lc
                d = lpc * lb + lpd * ld
            }

            else -> { // NO_SCALE / NO_SCALE_OR_REFLECTION
                val cos = cos_deg(rotation)
                val sin = sin_deg(rotation)
                var za = (pa * cos + pb * sin) / skeleton.scale_x
                var zc = (pc * cos + pd * sin) / skeleton.scale_y
                var s = sqrt(za * za + zc * zc)
                if (s > 0.00001) {
                    s = 1.0 / s
                }
                za *= s
                zc *= s
                s = sqrt(za * za + zc * zc)
                if (data.transform_mode == TRANSFORM_NO_SCALE &&
                    ((pa * pd - pb * pc < 0) != (skeleton.scale_x < 0 != (skeleton.scale_y < 0)))
                ) {
                    s = -s
                }
                val r = PI / 2 + atan2(zc, za)
                val zb = cos(r) * s
                val zd = sin(r) * s
                val la = cos_deg(shear_x) * scale_x
                val lb = cos_deg(90 + shear_y) * scale_y
                val lc = sin_deg(shear_x) * scale_x
                val ld = sin_deg(90 + shear_y) * scale_y
                a = za * la + zb * lc
                b = za * lb + zb * ld
                c = zc * la + zd * lc
                d = zc * lb + zd * ld
            }
        }
        a *= skeleton.scale_x
        b *= skeleton.scale_x
        c *= skeleton.scale_y
        d *= skeleton.scale_y
    }
}

class Slot(val data: SlotData, val bone: Bone) {
    var color: Color = Color(data.color.r, data.color.g, data.color.b, data.color.a)
    var dark_color: Color? = data.dark_color?.let {
        Color(it.r, it.g, it.b, 1.0)
    }
    var attachment: Attachment? = null
    var deform: DoubleArray = DoubleArray(0) // 变形缓冲区（DeformTimeline 写入，顶点计算消费）

    fun set_attachment(attachment: Attachment?) {
        this.attachment = attachment
        deform = DoubleArray(0)
    }
}

interface Updatable {
    fun update()
}

class Skeleton(val data: SkeletonData) {
    val bones: MutableList<Bone> = mutableListOf()
    val slots: MutableList<Slot> = mutableListOf()
    var draw_order: MutableList<Slot> = mutableListOf()
    var x: Double
    var y: Double
    var scale_x: Double = 1.0
    var scale_y: Double = 1.0
    var color: Color = Color(1.0, 1.0, 1.0, 1.0)
    // 持久约束实例（动画层会写入 mix/position 等属性）
    val ik_constraints: MutableList<IkConstraint> = mutableListOf()
    val transform_constraints: MutableList<TransformConstraint> = mutableListOf()
    val path_constraints: MutableList<PathConstraint> = mutableListOf()
    val update_cache: MutableList<Updatable> = mutableListOf()
    val update_cache_reset: MutableList<Bone> = mutableListOf()

    init {
        x = data.x
        y = data.y
        for (bone_data in data.bones) {
            val parent = bone_data.parent?.let { bones[it.index] }
            bones.add(Bone(bone_data, this, parent))
        }
        for (bone in bones) {
            bone.parent?.children?.add(bone)
        }
        for (slot_data in data.slots) {
            slots.add(Slot(slot_data, bones[slot_data.bone_data.index]))
        }
        draw_order = slots.toMutableList()
        for (d in data.ik_constraints) {
            ik_constraints.add(IkConstraint(d, this))
        }
        for (d in data.transform_constraints) {
            transform_constraints.add(TransformConstraint(d, this))
        }
        for (d in data.path_constraints) {
            path_constraints.add(PathConstraint(d, this))
        }
        _build_update_cache()
    }

    // ── 更新缓存（照 spine-ts 3.8 Skeleton.updateCache 移植） ──
    private fun _sort_bone(bone: Bone) {
        if (bone.sorted) {
            return
        }
        bone.parent?.let { _sort_bone(it) }
        bone.sorted = true
        update_cache.add(bone)
    }

    private fun _sort_reset(bones: List<Bone>) {
        for (bone in bones) {
            if (!bone.active) {
                continue
            }
            if (bone.sorted) {
                _sort_reset(bone.children)
            }
            bone.sorted = false
        }
    }

    private fun _sort_ik_constraint(constraint: IkConstraint) {
        constraint.active = constraint.target.active
        if (!constraint.active) {
            return
        }
        _sort_bone(constraint.target)
        val constrained = constraint.bones
        val parent = constrained[0]
        _sort_bone(parent)
        if (constrained.size > 1) {
            val child = constrained[constrained.size - 1]
            if (!update_cache.contains(child)) {
                update_cache_reset.add(child)
            }
        }
        update_cache.add(constraint)
        _sort_reset(parent.children)
        constrained[constrained.size - 1].sorted = true
    }

    private fun _sort_transform_constraint(constraint: TransformConstraint) {
        constraint.active = constraint.target.active
        if (!constraint.active) {
            return
        }
        _sort_bone(constraint.target)
        val constrained = constraint.bones
        if (constraint.data.local) {
            for (child in constrained) {
                _sort_bone(child.parent!!)
                if (!update_cache.contains(child)) {
                    update_cache_reset.add(child)
                }
            }
        } else {
            for (child in constrained) {
                _sort_bone(child)
            }
        }
        update_cache.add(constraint)
        for (child in constrained) {
            _sort_reset(child.children)
        }
        for (child in constrained) {
            child.sorted = true
        }
    }

    private fun _sort_path_constraint(constraint: PathConstraint) {
        constraint.active = constraint.target.bone.active
        if (!constraint.active) {
            return
        }
        val constrained = constraint.bones
        for (bone in constrained) {
            _sort_bone(bone)
        }
        update_cache.add(constraint)
        for (bone in constrained) {
            _sort_reset(bone.children)
        }
        for (bone in constrained) {
            bone.sorted = true
        }
    }

    private fun _build_update_cache() {
        for (bone in bones) {
            bone.sorted = bone.data.skin_required
            bone.active = !bone.sorted
        }
        val ik = ik_constraints
        val tx = transform_constraints
        val path = path_constraints
        val constraint_count = ik.size + tx.size + path.size
        for (i in 0 until constraint_count) {
            var found = false
            for (c in ik) {
                if (c.data.order == i) {
                    _sort_ik_constraint(c)
                    found = true
                    break
                }
            }
            if (found) {
                continue
            }
            for (c in tx) {
                if (c.data.order == i) {
                    _sort_transform_constraint(c)
                    found = true
                    break
                }
            }
            if (found) {
                continue
            }
            for (c in path) {
                if (c.data.order == i) {
                    _sort_path_constraint(c)
                    break
                }
            }
        }
        for (bone in bones) {
            _sort_bone(bone)
        }
    }

    fun update_world_transform() {
        for (bone in update_cache_reset) {
            bone.ax = bone.x
            bone.ay = bone.y
            bone.arotation = bone.rotation
            bone.ascale_x = bone.scale_x
            bone.ascale_y = bone.scale_y
            bone.ashear_x = bone.shear_x
            bone.ashear_y = bone.shear_y
            bone.applied_valid = true
        }
        for (updatable in update_cache) {
            updatable.update()
        }
    }

    fun set_to_setup_pose() {
        for (bone in bones) {
            bone.x = bone.data.x
            bone.y = bone.data.y
            bone.rotation = bone.data.rotation
            bone.scale_x = bone.data.scale_x
            bone.scale_y = bone.data.scale_y
            bone.shear_x = bone.data.shear_x
            bone.shear_y = bone.data.shear_y
        }
        for (c in ik_constraints) {
            c.mix = c.data.mix
            c.softness = c.data.softness
            c.bend_direction = c.data.bend_direction
            c.compress = c.data.compress
            c.stretch = c.data.stretch
            c.uniform = c.data.uniform
        }
        for (c in transform_constraints) {
            c.rotate_mix = c.data.rotate_mix
            c.translate_mix = c.data.translate_mix
            c.scale_mix = c.data.scale_mix
            c.shear_mix = c.data.shear_mix
        }
        for (c in path_constraints) {
            c.position = c.data.position
            c.spacing = c.data.spacing
            c.rotate_mix = c.data.rotate_mix
            c.translate_mix = c.data.translate_mix
        }
        for (slot in slots) {
            slot.color.set(slot.data.color.r, slot.data.color.g, slot.data.color.b, slot.data.color.a)
            val name = slot.data.attachment_name
            slot.attachment = name?.let { get_attachment(slot.data.index, it) }
        }
        for (slot in slots) {
            slot.deform = DoubleArray(0)
        }
        draw_order = slots.toMutableList()
    }

    fun get_attachment(slot_index: Int, name: String): Attachment? {
        return data.default_skin?.get_attachment(slot_index, name)
    }

    fun find_bone(name: String): BoneData? = data.find_bone(name)

    // 计算包围盒（不含约束），返回 (offset_x, offset_y, w, h)
    fun get_bounds(): DoubleArray {
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (slot in draw_order) {
            val att = slot.attachment ?: continue
            val wvl = if (att is VertexAttachment) att.world_vertices_length else 0
            val count = if (wvl != 0) wvl else 8
            val verts = compute_attachment_vertices(slot, att, 0, count)
            var v = 0
            while (v < verts.size) {
                xs.add(verts[v])
                ys.add(verts[v + 1])
                v += 2
            }
        }
        if (xs.isEmpty()) {
            return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
        var min_x = xs[0]
        var min_y = ys[0]
        var max_x = xs[0]
        var max_y = ys[0]
        for (i in 1 until xs.size) {
            if (xs[i] < min_x) min_x = xs[i]
            if (xs[i] > max_x) max_x = xs[i]
            if (ys[i] < min_y) min_y = ys[i]
            if (ys[i] > max_y) max_y = ys[i]
        }
        return doubleArrayOf(min_x, min_y, max_x - min_x, max_y - min_y)
    }
}

// 区域附件 4 角世界坐标，返回 8 元素列表
fun compute_region_vertices(slot: Slot, region_attachment: RegionAttachment, out: DoubleArray? = null): DoubleArray {
    val bone = slot.bone
    val off = region_attachment.offset
    val x = bone.world_x
    val y = bone.world_y
    val a = bone.a
    val b = bone.b
    val c = bone.c
    val d = bone.d
    val o = out ?: DoubleArray(8)
    o[0] = off[0] * a + off[1] * b + x
    o[1] = off[0] * c + off[1] * d + y
    o[2] = off[2] * a + off[3] * b + x
    o[3] = off[2] * c + off[3] * d + y
    o[4] = off[4] * a + off[5] * b + x
    o[5] = off[4] * c + off[5] * d + y
    o[6] = off[6] * a + off[7] * b + x
    o[7] = off[6] * c + off[7] * d + y
    return o
}

// 网格附件世界顶点（含变形）。变形值在 slot.deform 缓冲区。
fun compute_mesh_vertices(
    slot: Slot, mesh: VertexAttachment, start: Int = 0, count: Int = -1,
    out: DoubleArray? = null
): DoubleArray {
    val skeleton_bones = slot.bone.skeleton.bones
    val deform = slot.deform
    val bones = mesh.bones
    var vertices = mesh.vertices
    var vcount = count
    if (vcount < 0) {
        vcount = floor(mesh.world_vertices_length / 2.0).toInt()
    }
    val o = out ?: DoubleArray(vcount * 2)
    if (bones == null) {
        // 非加权：deform 非空时替换顶点数组（绝对坐标语义）
        if (deform.isNotEmpty()) {
            vertices = deform
        }
        val a = slot.bone.a
        val b = slot.bone.b
        val c = slot.bone.c
        val d = slot.bone.d
        val wx = slot.bone.world_x
        val wy = slot.bone.world_y
        for (v in start until start + vcount) {
            val vx = vertices!![v * 2]
            val vy = vertices!![v * 2 + 1]
            o[(v - start) * 2] = vx * a + vy * b + wx
            o[(v - start) * 2 + 1] = vx * c + vy * d + wy
        }
        return o
    }
    // 加权：vertices 存 (x, y, weight) 三元组，bones 存 [数量, 骨骼索引...]；
    // deform 增量按权重条目序号索引（官方 spine-ts 语义——按顶点索引是眼白泄漏根因，2026-08-20 修复）
    var oi = 0
    var bi = 0
    var vi = 0
    var f = 0
    var v = start
    if (start > 0) {
        for (s in 0 until start) {
            val n = bones!![bi]
            bi += n + 1
            f += n
        }
        vi = f * 3
    }
    while (v < start + vcount) {
        val bone_count = bones!![bi]
        bi += 1
        var wx = 0.0
        var wy = 0.0
        for (j in 0 until bone_count) {
            val bone_index = bones!![bi]
            bi += 1
            val bone = skeleton_bones[bone_index]
            val vx: Double
            val vy: Double
            if (deform.isNotEmpty()) {
                vx = vertices!![vi] + deform[f * 2]
                vy = vertices!![vi + 1] + deform[f * 2 + 1]
            } else {
                vx = vertices!![vi]
                vy = vertices!![vi + 1]
            }
            val weight = vertices!![vi + 2]
            vi += 3
            f += 1
            wx += (vx * bone.a + vy * bone.b + bone.world_x) * weight
            wy += (vx * bone.c + vy * bone.d + bone.world_y) * weight
        }
        o[oi] = wx
        o[oi + 1] = wy
        oi += 2
        v += 1
    }
    return o
}

// 统一入口：根据附件类型计算世界顶点
fun compute_attachment_vertices(slot: Slot, attachment: Attachment, start: Int, count: Int): DoubleArray {
    if (attachment is RegionAttachment) {
        return compute_region_vertices(slot, attachment)
    }
    if (attachment is MeshAttachment) {
        val vcount = if (count != 0) floor(count / 2.0).toInt() else -1
        return compute_mesh_vertices(slot, attachment, start, vcount)
    }
    if (attachment is ClippingAttachment) {
        val vcount = if (count != 0) floor(count / 2.0).toInt() else -1
        return compute_mesh_vertices(slot, attachment, start, vcount)
    }
    return DoubleArray(0)
}
