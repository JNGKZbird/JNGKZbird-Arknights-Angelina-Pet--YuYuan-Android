package com.jngkzbird.arknights_angelina_pet.spine38

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spine 3.8 约束求解 — IK / 变换 / 路径约束，精确移植自 spine38/constraints.ets（spine-ts 3.8）。
 * 与 Python 同构（含骨骼与约束交错执行语义）。
 */

private const val DEG_RAD = PI / 180.0
private const val RAD_DEG = 180.0 / PI
private const val PI2 = PI * 2

// ── IK 约束 ─────────────────────────────────────────────
class IkConstraint(val data: IkConstraintData, skeleton: Skeleton) : Updatable {
    val bones: MutableList<Bone> = mutableListOf()
    val target: Bone
    var mix: Double
    var softness: Double
    var bend_direction: Int
    var compress: Boolean
    var stretch: Boolean
    var uniform: Boolean
    var active: Boolean = false

    init {
        for (bd in data.bones) {
            bones.add(skeleton.bones[bd.index])
        }
        target = skeleton.bones[data.target!!.index]
        mix = data.mix
        softness = data.softness
        bend_direction = data.bend_direction
        compress = data.compress
        stretch = data.stretch
        uniform = data.uniform
    }

    fun apply() {
        val target = this.target
        val bones = this.bones
        if (bones.size == 1) {
            _apply1(bones[0], target.world_x, target.world_y, compress, stretch, uniform)
        } else if (bones.size == 2) {
            _apply2(bones[0], bones[1], target.world_x, target.world_y)
        }
    }

    override fun update() {
        apply()
    }

    private fun _apply1(
        bone: Bone, target_x: Double, target_y: Double, compress: Boolean, stretch: Boolean,
        uniform: Boolean
    ) {
        if (!bone.applied_valid) {
            bone.update_applied_transform()
        }
        val p = bone.parent!!
        var pa0 = p.a
        var pb0 = p.b
        var pc0 = p.c
        var pd0 = p.d
        var rotation_ik = -bone.ashear_x - bone.arotation
        var tx = 0.0
        var ty = 0.0
        val mode = bone.data.transform_mode
        if (mode == TRANSFORM_ONLY_TRANSLATION) {
            tx = target_x - bone.world_x
            ty = target_y - bone.world_y
        } else if (mode == TRANSFORM_NO_ROTATION_OR_REFLECTION) {
            val s = abs(pa0 * pd0 - pb0 * pc0) / (pa0 * pa0 + pc0 * pc0)
            val sa = pa0 / bone.skeleton.scale_x
            val sc = pc0 / bone.skeleton.scale_y
            pb0 = -sc * s * bone.skeleton.scale_x
            pd0 = sa * s * bone.skeleton.scale_y
            rotation_ik += atan2(sc, sa) * RAD_DEG
        } else {
            val x = target_x - p.world_x
            val y = target_y - p.world_y
            val d = pa0 * pd0 - pb0 * pc0
            if (abs(d) < 0.0001) {
                return
            }
            tx = (x * pd0 - y * pb0) / d - bone.ax
            ty = (y * pa0 - x * pc0) / d - bone.ay
        }
        rotation_ik += atan2(ty, tx) * RAD_DEG
        if (bone.ascale_x < 0) {
            rotation_ik += 180
        }
        if (rotation_ik > 180) {
            rotation_ik -= 360
        } else if (rotation_ik < -180) {
            rotation_ik += 360
        }
        var sx = bone.ascale_x
        var sy = bone.ascale_y
        if (compress || stretch) {
            if (mode == TRANSFORM_NO_SCALE || mode == TRANSFORM_NO_SCALE_OR_REFLECTION) {
                tx = target_x - bone.world_x
                ty = target_y - bone.world_y
            }
            val b = bone.data.length * sx
            val dd = sqrt(tx * tx + ty * ty)
            if (((compress && dd < b) || (stretch && dd > b)) && b > 0.0001) {
                val s = (dd / b - 1) * mix + 1
                sx *= s
                if (uniform) {
                    sy *= s
                }
            }
        }
        bone._update_with(bone.ax, bone.ay, bone.arotation + rotation_ik * mix, sx, sy, bone.ashear_x, bone.ashear_y)
    }

    private fun _apply2(parent: Bone, child: Bone, target_x: Double, target_y: Double) {
        if (mix == 0.0) {
            child.update_world_transform()
            return
        }
        if (!parent.applied_valid) {
            parent.update_applied_transform()
        }
        if (!child.applied_valid) {
            child.update_applied_transform()
        }
        val px = parent.ax
        val py = parent.ay
        var psx = parent.ascale_x
        var sx = parent.ascale_x
        var psy = parent.ascale_y
        var csx = child.ascale_x
        var os1: Double
        var os2: Double
        var s2: Double
        if (psx < 0) {
            psx = -psx
            os1 = 180.0
            s2 = -1.0
        } else {
            os1 = 0.0
            s2 = 1.0
        }
        if (psy < 0) {
            psy = -psy
            s2 = -s2
        }
        if (csx < 0) {
            csx = -csx
            os2 = 180.0
        } else {
            os2 = 0.0
        }
        val cx = child.ax
        var cy = 0.0
        var cwx: Double
        var cwy: Double
        var a = parent.a
        var b = parent.b
        var c = parent.c
        var d = parent.d
        val u = abs(psx - psy) <= 0.0001
        if (!u) {
            cy = 0.0
            cwx = a * cx + parent.world_x
            cwy = c * cx + parent.world_y
        } else {
            cy = child.ay
            cwx = a * cx + b * cy + parent.world_x
            cwy = c * cx + d * cy + parent.world_y
        }
        val pp = parent.parent!!
        a = pp.a
        b = pp.b
        c = pp.c
        d = pp.d
        val det = a * d - b * c
        if (abs(det) < 0.0001) {
            return
        }
        val id = 1.0 / det
        val x = cwx - pp.world_x
        val y = cwy - pp.world_y
        val dx = (x * d - y * b) * id - px
        val dy = (y * a - x * c) * id - py
        val l1 = sqrt(dx * dx + dy * dy)
        val l2 = child.data.length * csx
        var a1 = 0.0
        var a2 = 0.0
        if (l1 < 0.0001) {
            _apply1(parent, target_x, target_y, false, stretch, false)
            child._update_with(cx, cy, 0.0, child.ascale_x, child.ascale_y, child.ashear_x, child.ashear_y)
            return
        }
        val xx = target_x - pp.world_x
        val yy = target_y - pp.world_y
        var tx = (xx * d - yy * b) * id - px
        var ty = (yy * a - xx * c) * id - py
        var dd = tx * tx + ty * ty
        var softness = this.softness
        if (softness != 0.0) {
            softness *= psx * (csx + 1) / 2
            val td = sqrt(dd)
            val sd = td - l1 - l2 * psx + softness
            if (sd > 0) {
                var p = min(1.0, sd / (softness * 2)) - 1
                p = (sd - softness * (1 - p * p)) / td
                tx -= p * tx
                ty -= p * ty
                dd = tx * tx + ty * ty
            }
        }
        if (u) {
            var l2u = l2 * psx
            var cos = (dd - l1 * l1 - l2u * l2u) / (2 * l1 * l2u)
            if (cos < -1.0) {
                cos = -1.0
            } else if (cos > 1.0) {
                cos = 1.0
                if (stretch) {
                    sx *= (sqrt(dd) / (l1 + l2u) - 1) * mix + 1
                }
            }
            a2 = acos(cos) * bend_direction.toDouble()
            a = l1 + l2u * cos
            b = l2u * sin(a2)
            a1 = atan2(ty * a - tx * b, tx * a + ty * b)
        } else {
            a = psx * l2
            b = psy * l2
            val aa = a * a
            val bb = b * b
            val ta = atan2(ty, tx)
            val qc = bb * l1 * l1 + aa * dd - aa * bb
            val qc1 = -2 * bb * l1
            val qc2 = bb - aa
            val qd = qc1 * qc1 - 4 * qc2 * qc
            var outer_break = false
            if (qd >= 0) {
                var q = sqrt(qd)
                if (qc1 < 0) {
                    q = -q
                }
                q = -(qc1 + q) / 2
                val r0 = q / qc2
                val r1 = qc / q
                val r = if (abs(r0) < abs(r1)) r0 else r1
                if (r * r <= dd) {
                    val ny = sqrt(dd - r * r) * bend_direction.toDouble()
                    a1 = ta - atan2(ny, r)
                    a2 = atan2(ny / psy, (r - l1) / psx)
                    outer_break = true
                }
            }
            if (!outer_break) {
                var min_angle = PI
                var min_x = l1 - a
                var min_dist = min_x * min_x
                var min_y = 0.0
                var max_angle = 0.0
                var max_x = l1 + a
                var max_dist = max_x * max_x
                var max_y = 0.0
                var nc = if (abs(aa - bb) > 0.0001) -a * l1 / (aa - bb) else 0.0
                if (-1 <= nc && nc <= 1) {
                    nc = acos(nc)
                    val nx = a * cos(nc) + l1
                    val ny = b * sin(nc)
                    val nd = nx * nx + ny * ny
                    if (nd < min_dist) {
                        min_angle = nc
                        min_dist = nd
                        min_x = nx
                        min_y = ny
                    }
                    if (nd > max_dist) {
                        max_angle = nc
                        max_dist = nd
                        max_x = nx
                        max_y = ny
                    }
                }
                if (dd <= (min_dist + max_dist) / 2) {
                    a1 = ta - atan2(min_y * bend_direction.toDouble(), min_x)
                    a2 = min_angle * bend_direction.toDouble()
                } else {
                    a1 = ta - atan2(max_y * bend_direction.toDouble(), max_x)
                    a2 = max_angle * bend_direction.toDouble()
                }
            }
        }
        val os = atan2(cy, cx) * s2
        var rotation = parent.arotation
        a1 = (a1 - os) * RAD_DEG + os1 - rotation
        if (a1 > 180) {
            a1 -= 360
        } else if (a1 < -180) {
            a1 += 360
        }
        parent._update_with(px, py, rotation + a1 * mix, sx, parent.ascale_y, 0.0, 0.0)
        rotation = child.arotation
        a2 = ((a2 + os) * RAD_DEG - child.ashear_x) * s2 + os2 - rotation
        if (a2 > 180) {
            a2 -= 360
        } else if (a2 < -180) {
            a2 += 360
        }
        child._update_with(cx, cy, rotation + a2 * mix, child.ascale_x, child.ascale_y, child.ashear_x, child.ashear_y)
    }
}

// ── 变换约束 ────────────────────────────────────────────
class TransformConstraint(val data: TransformConstraintData, skeleton: Skeleton) : Updatable {
    val bones: MutableList<Bone> = mutableListOf()
    val target: Bone
    var rotate_mix: Double
    var translate_mix: Double
    var scale_mix: Double
    var shear_mix: Double
    var active: Boolean = false

    init {
        for (bd in data.bones) {
            bones.add(skeleton.bones[bd.index])
        }
        target = skeleton.bones[data.target!!.index]
        rotate_mix = data.rotate_mix
        translate_mix = data.translate_mix
        scale_mix = data.scale_mix
        shear_mix = data.shear_mix
    }

    fun apply() {
        // 本模型数据均为 local=false, relative=false → applyAbsoluteWorld
        _apply_absolute_world()
    }

    override fun update() {
        apply()
    }

    private fun _apply_absolute_world() {
        val rotate_mix = this.rotate_mix
        val translate_mix = this.translate_mix
        val scale_mix = this.scale_mix
        val shear_mix = this.shear_mix
        val target = this.target
        val ta = target.a
        val tb = target.b
        val tc = target.c
        val td = target.d
        val deg_rad_reflect = if (ta * td - tb * tc > 0) DEG_RAD else -DEG_RAD
        val offset_rotation = data.offset_rotation * deg_rad_reflect
        val offset_shear_y = data.offset_shear_y * deg_rad_reflect
        for (bone in bones) {
            if (rotate_mix != 0.0) {
                val a = bone.a
                val b = bone.b
                val c = bone.c
                val d = bone.d
                var r = atan2(tc, ta) - atan2(c, a) + offset_rotation
                if (r > PI) {
                    r -= PI2
                } else if (r < -PI) {
                    r += PI2
                }
                r *= rotate_mix
                val cos = cos(r)
                val sin = sin(r)
                bone.a = cos * a - sin * c
                bone.b = cos * b - sin * d
                bone.c = sin * a + cos * c
                bone.d = sin * b + cos * d
                bone.applied_valid = false
            }
            if (translate_mix != 0.0) {
                // target.localToWorld(offsetX, offsetY)
                val ox = data.offset_x
                val oy = data.offset_y
                val tx = target.a * ox + target.b * oy + target.world_x
                val ty = target.c * ox + target.d * oy + target.world_y
                bone.world_x += (tx - bone.world_x) * translate_mix
                bone.world_y += (ty - bone.world_y) * translate_mix
                bone.applied_valid = false
            }
            if (scale_mix > 0) {
                var s = sqrt(bone.a * bone.a + bone.c * bone.c)
                val ts = sqrt(ta * ta + tc * tc)
                if (s > 0.00001) {
                    s = (s + (ts - s + data.offset_scale_x) * scale_mix) / s
                }
                bone.a *= s
                bone.c *= s
                s = sqrt(bone.b * bone.b + bone.d * bone.d)
                val ts2 = sqrt(tb * tb + td * td)
                if (s > 0.00001) {
                    s = (s + (ts2 - s + data.offset_scale_y) * scale_mix) / s
                }
                bone.b *= s
                bone.d *= s
                bone.applied_valid = false
            }
            if (shear_mix > 0) {
                val b = bone.b
                val d = bone.d
                val by = atan2(d, b)
                var r = atan2(td, tb) - atan2(tc, ta) - (by - atan2(bone.c, bone.a))
                if (r > PI) {
                    r -= PI2
                } else if (r < -PI) {
                    r += PI2
                }
                r = by + (r + offset_shear_y) * shear_mix
                val s = sqrt(b * b + d * d)
                bone.b = cos(r) * s
                bone.d = sin(r) * s
                bone.applied_valid = false
            }
        }
    }
}

// ── 路径约束 ────────────────────────────────────────────
class PathConstraint(val data: PathConstraintData, skeleton: Skeleton) : Updatable {
    companion object {
        const val NONE: Int = -1
        const val BEFORE: Int = -2
        const val AFTER: Int = -3
        const val epsilon: Double = 0.00001
    }

    val bones: MutableList<Bone> = mutableListOf()
    val target: Slot
    var position: Double
    var spacing: Double
    var rotate_mix: Double
    var translate_mix: Double
    var active: Boolean = false
    var spaces: DoubleArray = DoubleArray(8)
    var positions: DoubleArray = DoubleArray(8)
    var world: DoubleArray = DoubleArray(8)
    var lengths: DoubleArray = DoubleArray(8)

    init {
        for (bd in data.bones) {
            bones.add(skeleton.bones[bd.index])
        }
        target = skeleton.slots[data.target!!.index]
        position = data.position
        spacing = data.spacing
        rotate_mix = data.rotate_mix
        translate_mix = data.translate_mix
    }

    fun apply() {
        update()
    }

    override fun update() {
        val attachment = target.attachment
        if (attachment !is PathAttachment) {
            return
        }
        val rotate_mix = this.rotate_mix
        val translate_mix = this.translate_mix
        val translate = translate_mix > 0
        val rotate = rotate_mix > 0
        if (!translate && !rotate) {
            return
        }
        val data = this.data
        val percent_spacing = data.spacing_mode == 2 // Percent
        val rotate_mode = data.rotate_mode
        val tangents = rotate_mode == ROTATE_TANGENT
        val scale = rotate_mode == ROTATE_CHAIN_SCALE
        val bone_count = bones.size
        val spaces_count = if (tangents) bone_count else bone_count + 1
        val bones = this.bones
        var spaces = spaces
        if (spaces.size < spaces_count) {
            spaces = spaces.copyOf(spaces_count)
            this.spaces = spaces
        }
        var lengths: DoubleArray? = null
        val spacing = this.spacing
        if (scale || !percent_spacing) {
            if (scale) {
                if (this.lengths.size < bone_count) {
                    this.lengths = this.lengths.copyOf(bone_count)
                }
                lengths = this.lengths
            }
            val length_spacing = data.spacing_mode == SPACING_LENGTH
            for (i in 0 until spaces_count - 1) {
                val bone = bones[i]
                val setup_length = bone.data.length
                if (setup_length < epsilon) {
                    if (scale) {
                        lengths!![i] = 0.0
                    }
                    spaces[i + 1] = 0.0
                } else if (percent_spacing) {
                    if (scale) {
                        val x = setup_length * bone.a
                        val y = setup_length * bone.c
                        lengths!![i] = sqrt(x * x + y * y)
                    }
                    spaces[i + 1] = spacing
                } else {
                    val x = setup_length * bone.a
                    val y = setup_length * bone.c
                    val length = sqrt(x * x + y * y)
                    if (scale) {
                        lengths!![i] = length
                    }
                    spaces[i + 1] = (if (length_spacing) (setup_length + spacing) else spacing) * length / setup_length
                }
            }
        } else {
            for (i in 1 until spaces_count) {
                spaces[i] = spacing
            }
        }
        val positions = _compute_world_positions(
            attachment, spaces_count, tangents,
            data.position_mode == 1, percent_spacing
        )
        var bone_x = positions[0]
        var bone_y = positions[1]
        var offset_rotation = data.offset_rotation
        val tip: Boolean
        if (offset_rotation == 0.0) {
            tip = rotate_mode == ROTATE_CHAIN
        } else {
            tip = false
            val p = target.bone
            offset_rotation *= if (p.a * p.d - p.b * p.c > 0) DEG_RAD else -DEG_RAD
        }
        for (i in 0 until bone_count) {
            val bone = bones[i]
            bone.world_x += (bone_x - bone.world_x) * translate_mix
            bone.world_y += (bone_y - bone.world_y) * translate_mix
            val x = positions[(i + 1) * 3]
            val y = positions[(i + 1) * 3 + 1]
            val dx = x - bone_x
            val dy = y - bone_y
            if (scale) {
                val length = lengths!![i]
                if (length != 0.0) {
                    val s = (sqrt(dx * dx + dy * dy) / length - 1) * rotate_mix + 1
                    bone.a *= s
                    bone.c *= s
                }
            }
            bone_x = x
            bone_y = y
            if (rotate) {
                val a = bone.a
                val b = bone.b
                val c = bone.c
                val d = bone.d
                var r: Double
                if (tangents) {
                    r = positions[(i + 1) * 3 - 1]
                } else if (spaces[i + 1] == 0.0) {
                    r = positions[(i + 1) * 3 + 2]
                } else {
                    r = atan2(dy, dx)
                }
                r -= atan2(c, a)
                if (tip) {
                    val cos = cos(r)
                    val sin = sin(r)
                    val length = bone.data.length
                    bone_x += (length * (cos * a - sin * c) - dx) * rotate_mix
                    bone_y += (length * (sin * a + cos * c) - dy) * rotate_mix
                } else {
                    r += offset_rotation
                }
                if (r > PI) {
                    r -= PI2
                } else if (r < -PI) {
                    r += PI2
                }
                r *= rotate_mix
                val cos = cos(r)
                val sin = sin(r)
                bone.a = cos * a - sin * c
                bone.b = cos * b - sin * d
                bone.c = sin * a + cos * c
                bone.d = sin * b + cos * d
            }
            bone.applied_valid = false
        }
    }

    private fun _compute_world_positions(
        path: PathAttachment, spaces_count: Int, tangents: Boolean,
        percent_position: Boolean, percent_spacing: Boolean
    ): DoubleArray {
        val target = this.target
        var position = this.position
        val spaces = this.spaces
        var positions = positions
        if (positions.size < spaces_count * 3 + 2) {
            positions = positions.copyOf(spaces_count * 3 + 2)
            this.positions = positions
        }
        val out = positions
        val closed = path.closed
        val vertices_length = path.world_vertices_length
        var curve_count = vertices_length / 6
        var prev_curve = NONE

        fun compute_verts(start: Int, count: Int): DoubleArray {
            return compute_mesh_vertices(target, path, start / 2, count / 2)
        }

        if (!path.constant_speed) {
            val lengths = path.lengths!!
            curve_count -= if (closed) 1 else 2
            val path_length = lengths[curve_count]
            if (percent_position) {
                position *= path_length
            }
            if (percent_spacing) {
                for (i in 1 until spaces_count) {
                    spaces[i] *= path_length
                }
            }
            var world = this.world
            if (world.size < 8) {
                world = DoubleArray(8)
                this.world = world
            }
            var o = 0
            var curve = 0
            for (i in 0 until spaces_count) {
                val space = spaces[i]
                position += space
                var p = position
                if (closed) {
                    p %= path_length
                    if (p < 0) {
                        p += path_length
                    }
                    curve = 0
                } else if (p < 0) {
                    if (prev_curve != BEFORE) {
                        prev_curve = BEFORE
                        val w = compute_verts(2, 4)
                        for (k in 0 until 4) {
                            world[k] = w[k]
                        }
                    }
                    _add_before_position(p, world, 0, out, o)
                    o += 3
                    continue
                } else if (p > path_length) {
                    if (prev_curve != AFTER) {
                        prev_curve = AFTER
                        val w = compute_verts(vertices_length - 6, 4)
                        for (k in 0 until 4) {
                            world[k] = w[k]
                        }
                    }
                    _add_after_position(p - path_length, world, 0, out, o)
                    o += 3
                    continue
                }
                while (true) {
                    val length = lengths[curve]
                    if (p > length) {
                        curve += 1
                        continue
                    }
                    if (curve == 0) {
                        p /= length
                    } else {
                        val prev = lengths[curve - 1]
                        p = (p - prev) / (length - prev)
                    }
                    break
                }
                if (curve != prev_curve) {
                    prev_curve = curve
                    if (closed && curve == curve_count) {
                        val w1 = compute_verts(vertices_length - 4, 4)
                        val w2 = compute_verts(0, 4)
                        for (k in 0 until 4) {
                            world[k] = w1[k]
                        }
                        for (k in 0 until 4) {
                            world[4 + k] = w2[k]
                        }
                    } else {
                        val w = compute_verts(curve * 6 + 2, 8)
                        for (k in 0 until 8) {
                            world[k] = w[k]
                        }
                    }
                }
                _add_curve_position(
                    p, world[0], world[1], world[2], world[3], world[4], world[5],
                    world[6], world[7], out, o, tangents || (i > 0 && space == 0.0)
                )
                o += 3
            }
            return out
        }
        // constantSpeed 分支（本模型不使用，省略）
        return out
    }

    private fun _add_before_position(p: Double, temp: DoubleArray, i: Int, out: DoubleArray, o: Int) {
        val x1 = temp[i]
        val y1 = temp[i + 1]
        val dx = temp[i + 2] - x1
        val dy = temp[i + 3] - y1
        val r = atan2(dy, dx)
        out[o] = x1 + p * cos(r)
        out[o + 1] = y1 + p * sin(r)
        out[o + 2] = r
    }

    private fun _add_after_position(p: Double, temp: DoubleArray, i: Int, out: DoubleArray, o: Int) {
        val x1 = temp[i + 2]
        val y1 = temp[i + 3]
        val dx = x1 - temp[i]
        val dy = y1 - temp[i + 1]
        val r = atan2(dy, dx)
        out[o] = x1 + p * cos(r)
        out[o + 1] = y1 + p * sin(r)
        out[o + 2] = r
    }

    private fun _add_curve_position(
        p: Double, x1: Double, y1: Double, cx1: Double, cy1: Double,
        cx2: Double, cy2: Double, x2: Double, y2: Double, out: DoubleArray, o: Int,
        tangents: Boolean
    ) {
        if (p == 0.0 || p.isNaN()) {
            out[o] = x1
            out[o + 1] = y1
            out[o + 2] = atan2(cy1 - y1, cx1 - x1)
            return
        }
        val tt = p * p
        val ttt = tt * p
        val u = 1 - p
        val uu = u * u
        val uuu = uu * u
        val ut = u * p
        val ut3 = ut * 3
        val uut3 = u * ut3
        val utt3 = ut3 * p
        val x = x1 * uuu + cx1 * uut3 + cx2 * utt3 + x2 * ttt
        val y = y1 * uuu + cy1 * uut3 + cy2 * utt3 + y2 * ttt
        out[o] = x
        out[o + 1] = y
        if (tangents) {
            if (p < 0.001) {
                out[o + 2] = atan2(cy1 - y1, cx1 - x1)
            } else {
                out[o + 2] = atan2(
                    y - (y1 * uu + cy1 * ut * 2 + cy2 * tt),
                    x - (x1 * uu + cx1 * ut * 2 + cx2 * tt)
                )
            }
        }
    }
}

// 兼容入口：使用骨架上的持久约束实例
fun apply_constraints(skeleton: Skeleton) {
    for (c in skeleton.ik_constraints) {
        c.apply()
    }
    for (c in skeleton.transform_constraints) {
        c.apply()
    }
    for (c in skeleton.path_constraints) {
        c.apply()
    }
}
