package com.jngkzbird.arknights_angelina_pet.spine38

import kotlin.math.abs
import kotlin.math.floor

/**
 * Spine 3.8 动画时间线应用 — 精确移植自 spine38/animation.ets（spine-ts 3.8）。
 * 槽位颜色/变形/路径曲线在 loader 读弃 → 恒线性。
 * Python 的 AnimationStateImpl/_frame_percent 未被引擎使用，Kotlin 版不移植（死代码）。
 */

// ── 曲线求值 ────────────────────────────────────────────
private fun _bezier(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
    val mt = 1.0 - t
    return mt * mt * mt * p0 + 3 * mt * mt * t * p1 + 3 * mt * t * t * p2 + t * t * t * p3
}

private fun _bezier_x(t: Double, cx1: Double, cx2: Double): Double = _bezier(t, 0.0, cx1, cx2, 1.0)

private fun _bezier_y(t: Double, cy1: Double, cy2: Double): Double = _bezier(t, 0.0, cy1, cy2, 1.0)

// 曲线时间映射：x∈[0,1]，返回插值进度（JS getCurvePercent 语义）
fun curve_percent(curve: Curve?, x: Double): Double {
    if (curve == null || curve.type == 0) { // linear
        return x
    }
    if (curve.type == 1) { // stepped
        return 0.0
    }
    val cx1 = curve.cx1
    val cy1 = curve.cy1
    val cx2 = curve.cx2
    val cy2 = curve.cy2
    val epsilon = 0.00001
    var t = x
    for (i in 0 until 8) {
        val xt = _bezier_x(t, cx1, cx2) - x
        if (abs(xt) < epsilon) {
            return _bezier_y(t, cy1, cy2)
        }
        val d = 3 * (1 - t) * (1 - t) * cx1 + 6 * (1 - t) * t * (cx2 - cx1) + 3 * t * t * (1 - cx2)
        if (abs(d) < 0.0001) {
            break
        }
        t -= xt / d
    }
    var lo = 0.0
    var hi = 1.0
    t = x
    if (t < lo) {
        return _bezier_y(lo, cy1, cy2)
    }
    if (t > hi) {
        return _bezier_y(hi, cy1, cy2)
    }
    while (lo < hi) {
        val xt = _bezier_x(t, cx1, cx2)
        if (abs(xt - x) < epsilon) {
            return _bezier_y(t, cy1, cy2)
        }
        if (x > xt) {
            lo = t
        } else {
            hi = t
        }
        t = (hi - lo) / 2 + lo
    }
    return _bezier_y(t, cy1, cy2)
}

// JS 的 16384 角度环绕技巧：包装到 [-180, 180)
private fun _wrap_angle(value: Double): Double {
    var v = value
    v -= (16384 - floor(16384.499999999996 - v / 360.0)) * 360.0
    return v
}

// 返回时间所在帧区间的【下一帧】索引（JS Animation.binarySearch 语义）
private fun _binary_search(frames: DoubleArray, time: Double, entries: Int): Int {
    val n = frames.size / entries
    if (time <= frames[0]) {
        return entries // 指向第二帧（若存在）
    }
    if (time >= frames[(n - 1) * entries]) {
        return (n - 1) * entries
    }
    var lo = 1
    var hi = n - 1
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (frames[mid * entries] <= time) {
            lo = mid + 1
        } else {
            hi = mid
        }
    }
    return lo * entries
}

private fun _lerp(a: Double, b: Double, p: Double): Double = a + (b - a) * p

private inline fun <T> _frame_times(frames: List<T>, timeOf: (T) -> Double): DoubleArray =
    DoubleArray(frames.size) { timeOf(frames[it]) }

// ── 时间线应用（JS blend=first + alpha 语义） ─────────────
fun apply_rotate(tl: BoneTimeline, skeleton: Skeleton, time: Double, alpha: Double = 1.0) {
    val frames = tl.frames
    val curves = tl.curves
    val bone = skeleton.bones[tl.bone]
    val n = frames.size
    if (time < frames[0].time) {
        // blend=first: 向 setup 值靠拢
        var r = bone.data.rotation - bone.rotation
        r = _wrap_angle(r)
        bone.rotation += r * alpha
        return
    }
    if (time >= frames[n - 1].time) {
        // PREV_ROTATION = 最后一帧自身的旋转值
        val prev_rot = frames[n - 1].f1
        var r = frames[n - 1].f1 - prev_rot
        r = _wrap_angle(r)
        r = prev_rot + r
        r += bone.data.rotation - bone.rotation
        bone.rotation += _wrap_angle(r) * alpha
        return
    }
    // 二分定位下一帧
    val ts = _frame_times(frames) { it.time }
    val frame = _binary_search(ts, time, 1)
    val fi = frame - 1 // 当前帧索引
    // PREV_ROTATION = 当前帧自身的旋转值
    val prev_rot = frames[fi].f1
    val frame_time = frames[fi].time
    val next_time = frames[fi + 1].time
    var percent = if (next_time > frame_time) (time - frame_time) / (next_time - frame_time) else 0.0
    val curve = if (fi < curves.size) curves[fi] else null
    percent = curve_percent(curve, percent)
    var r = frames[fi + 1].f1 - prev_rot
    r = _wrap_angle(r)
    r = prev_rot + r * percent
    r += bone.data.rotation - bone.rotation
    bone.rotation += _wrap_angle(r) * alpha
}

// 平移时间线：帧值是对 data 的偏移（blend=first 语义）
fun apply_translate(tl: BoneTimeline, skeleton: Skeleton, time: Double, alpha: Double = 1.0) {
    val frames = tl.frames
    val curves = tl.curves
    val bone = skeleton.bones[tl.bone]
    if (time < frames[0].time) {
        bone.x += (bone.data.x - bone.x) * alpha
        bone.y += (bone.data.y - bone.y) * alpha
        return
    }
    val n = frames.size
    var x: Double
    var y: Double
    if (time >= frames[n - 1].time) {
        x = frames[n - 1].f1
        y = frames[n - 1].f2
    } else {
        val ts = _frame_times(frames) { it.time }
        val frame = _binary_search(ts, time, 1)
        val fi = frame - 1
        val x0 = frames[fi].f1
        val y0 = frames[fi].f2
        val x1 = frames[fi + 1].f1
        val y1 = frames[fi + 1].f2
        val frame_time = frames[fi].time
        val next_time = frames[fi + 1].time
        var percent = if (next_time > frame_time) (time - frame_time) / (next_time - frame_time) else 0.0
        val curve = if (fi < curves.size) curves[fi] else null
        percent = curve_percent(curve, percent)
        x = _lerp(x0, x1, percent)
        y = _lerp(y0, y1, percent)
    }
    bone.x += (bone.data.x + x - bone.x) * alpha
    bone.y += (bone.data.y + y - bone.y) * alpha
}

// 缩放时间线：帧值是对 data 的倍率；剪切：帧值是对 data 的偏移
fun apply_scale_shear(tl: BoneTimeline, skeleton: Skeleton, time: Double, alpha: Double, is_scale: Boolean) {
    val frames = tl.frames
    val curves = tl.curves
    val bone = skeleton.bones[tl.bone]
    if (time < frames[0].time) {
        if (is_scale) {
            bone.scale_x += (bone.data.scale_x - bone.scale_x) * alpha
            bone.scale_y += (bone.data.scale_y - bone.scale_y) * alpha
        } else {
            bone.shear_x += (bone.data.shear_x - bone.shear_x) * alpha
            bone.shear_y += (bone.data.shear_y - bone.shear_y) * alpha
        }
        return
    }
    val n = frames.size
    var x: Double
    var y: Double
    if (time >= frames[n - 1].time) {
        x = frames[n - 1].f1
        y = frames[n - 1].f2
    } else {
        val ts = _frame_times(frames) { it.time }
        val frame = _binary_search(ts, time, 1)
        val fi = frame - 1
        val x0 = frames[fi].f1
        val y0 = frames[fi].f2
        val x1 = frames[fi + 1].f1
        val y1 = frames[fi + 1].f2
        val frame_time = frames[fi].time
        val next_time = frames[fi + 1].time
        var percent = if (next_time > frame_time) (time - frame_time) / (next_time - frame_time) else 0.0
        val curve = if (fi < curves.size) curves[fi] else null
        percent = curve_percent(curve, percent)
        x = _lerp(x0, x1, percent)
        y = _lerp(y0, y1, percent)
    }
    if (is_scale) {
        // 倍率语义：raw × data
        val vx = x * bone.data.scale_x
        val vy = y * bone.data.scale_y
        bone.scale_x += (vx - bone.scale_x) * alpha
        bone.scale_y += (vy - bone.scale_y) * alpha
    } else {
        // 偏移语义：data + raw
        val vx = bone.data.shear_x + x
        val vy = bone.data.shear_y + y
        bone.shear_x += (vx - bone.shear_x) * alpha
        bone.shear_y += (vy - bone.shear_y) * alpha
    }
}

fun apply_color(tl: SlotTimeline, skeleton: Skeleton, time: Double, alpha: Double = 1.0) {
    val frames = tl.color_frames
    val slot = skeleton.slots[tl.slot]
    val n = frames.size
    val c: Color
    if (time < frames[0].time) {
        c = frames[0].color
    } else if (time >= frames[n - 1].time) {
        c = frames[n - 1].color
    } else {
        val ts = _frame_times(frames) { it.time }
        val frame = _binary_search(ts, time, 1)
        val fi = frame - 1
        val c0 = frames[fi].color
        val c1 = frames[fi + 1].color
        val frame_time = frames[fi].time
        val next_time = frames[fi + 1].time
        var percent = if (next_time > frame_time) (time - frame_time) / (next_time - frame_time) else 0.0
        percent = curve_percent(null, percent) // 槽位颜色曲线在 loader 读弃 → 线性
        c = Color(_lerp(c0.r, c1.r, percent), _lerp(c0.g, c1.g, percent), _lerp(c0.b, c1.b, percent), _lerp(c0.a, c1.a, percent))
    }
    slot.color.r += (c.r - slot.color.r) * alpha
    slot.color.g += (c.g - slot.color.g) * alpha
    slot.color.b += (c.b - slot.color.b) * alpha
    slot.color.a += (c.a - slot.color.a) * alpha
}

fun apply_attachment(tl: SlotTimeline, skeleton: Skeleton, time: Double, alpha: Double) {
    val frames = tl.attachment_frames
    val slot_index = tl.slot
    var name = frames[0].name
    for (i in frames.indices) {
        if (time >= frames[i].time) {
            name = frames[i].name
        } else {
            break
        }
    }
    val slot = skeleton.slots[slot_index]
    if (alpha < 0.5) {
        return
    }
    slot.attachment = name?.let { skeleton.get_attachment(slot_index, it) }
}

// 变形写入 slot.deform 缓冲区（JS 语义）：非加权=绝对坐标，加权=增量
fun apply_deform(tl: DeformTimeline, skeleton: Skeleton, time: Double, alpha: Double = 1.0) {
    val frames = tl.frames
    val attachment = tl.attachment ?: return
    val slot_index = tl.slot
    val slot = skeleton.slots[slot_index]
    val cur_att = slot.attachment ?: return
    // JS 守卫：当前附件的变形目标必须匹配
    val target: Attachment =
        if (cur_att is MeshAttachment && cur_att.deform_attachment != null) cur_att.deform_attachment!! else cur_att
    if (target !== attachment) {
        return
    }
    if (time < frames[0].time) {
        // blend=first alpha=1：清空变形（回到基础顶点）
        slot.deform = DoubleArray(0)
        return
    }
    val n = frames.size
    val deform: DoubleArray
    if (time >= frames[n - 1].time) {
        deform = frames[n - 1].values
    } else {
        val ts = _frame_times(frames) { it.time }
        val frame = _binary_search(ts, time, 1)
        val fi = frame - 1
        val d0 = frames[fi].values
        val d1 = frames[fi + 1].values
        val frame_time = frames[fi].time
        val next_time = frames[fi + 1].time
        var percent = if (next_time > frame_time) (time - frame_time) / (next_time - frame_time) else 0.0
        percent = curve_percent(null, percent) // 变形曲线在 loader 读弃 → 线性
        deform = DoubleArray(d0.size) { _lerp(d0[it], d1[it], percent) }
    }
    if (alpha >= 1) {
        slot.deform = deform.copyOf()
    } else {
        if (slot.deform.size < deform.size) {
            slot.deform = DoubleArray(deform.size)
        }
        for (i in deform.indices) {
            slot.deform[i] += (deform[i] - slot.deform[i]) * alpha
        }
    }
}

fun apply_draw_order(tl: DrawOrderTimeline, skeleton: Skeleton, time: Double) {
    val frames = tl.frames
    val slot_count = skeleton.slots.size
    var offsets: List<DrawOrderOffset>
    if (time < frames[0].time) {
        offsets = frames[0].offsets
    } else if (time >= frames[frames.size - 1].time) {
        offsets = frames[frames.size - 1].offsets
    } else {
        offsets = frames[0].offsets
        for (i in frames.indices) {
            if (time >= frames[i].time) {
                offsets = frames[i].offsets
            } else {
                break
            }
        }
    }
    val draw_order = IntArray(slot_count) { -1 }
    val unchanged = ArrayList<Int>()
    var original_index = 0
    for (off_item in offsets) {
        val slot_i = off_item.slot
        val off = off_item.offset
        while (original_index != slot_i) {
            unchanged.add(original_index)
            original_index += 1
        }
        draw_order[original_index + off] = original_index
        original_index += 1
    }
    while (original_index < slot_count) {
        unchanged.add(original_index)
        original_index += 1
    }
    var ui = unchanged.size - 1
    for (i in slot_count - 1 downTo 0) {
        if (draw_order[i] == -1) {
            draw_order[i] = unchanged[ui]
            ui -= 1
        }
    }
    val new_order = ArrayList<Slot>(slot_count)
    for (i in 0 until slot_count) {
        new_order.add(skeleton.slots[draw_order[i]])
    }
    skeleton.draw_order = new_order
}

// ── 约束时间线 ──────────────────────────────────────────
fun apply_ik_timelines(animation: Animation, skeleton: Skeleton, time: Double, alpha: Double = 1.0) {
    for (tl in animation.ik_timelines) {
        val c = skeleton.ik_constraints[tl.index]
        val frames = tl.frames
        val curves = tl.curves
        val n = frames.size
        var mix: Double
        var softness: Double
        var bend: Double
        var compress: Double
        var stretch: Double
        if (time < frames[0].time) {
            mix = frames[0].mix
            softness = frames[0].softness
            bend = frames[0].bend.toDouble()
            compress = if (frames[0].compress) 1.0 else 0.0
            stretch = if (frames[0].stretch) 1.0 else 0.0
        } else if (time >= frames[n - 1].time) {
            mix = frames[n - 1].mix
            softness = frames[n - 1].softness
            bend = frames[n - 1].bend.toDouble()
            compress = if (frames[n - 1].compress) 1.0 else 0.0
            stretch = if (frames[n - 1].stretch) 1.0 else 0.0
        } else {
            val ts = _frame_times(frames) { it.time }
            val frame = _binary_search(ts, time, 1)
            val fi = frame - 1
            val f0 = frames[fi]
            val f1 = frames[fi + 1]
            val frame_time = f0.time
            val next_time = f1.time
            var percent = if (next_time > frame_time) (time - frame_time) / (next_time - frame_time) else 0.0
            val curve = if (fi < curves.size) curves[fi] else null
            percent = curve_percent(curve, percent)
            mix = _lerp(f0.mix, f1.mix, percent)
            softness = _lerp(f0.softness, f1.softness, percent)
            bend = _lerp(f0.bend.toDouble(), f1.bend.toDouble(), percent)
            compress = _lerp(if (f0.compress) 1.0 else 0.0, if (f1.compress) 1.0 else 0.0, percent)
            stretch = _lerp(if (f0.stretch) 1.0 else 0.0, if (f1.stretch) 1.0 else 0.0, percent)
        }
        c.mix += (mix - c.mix) * alpha
        c.softness += (softness - c.softness) * alpha
        if (alpha >= 1) {
            c.bend_direction = bend.toInt() // Math.trunc 语义（向零截断）
            c.compress = compress != 0.0
            c.stretch = stretch != 0.0
        }
    }
}

fun apply_transform_timelines(animation: Animation, skeleton: Skeleton, time: Double, alpha: Double = 1.0) {
    for (tl in animation.transform_timelines) {
        val c = skeleton.transform_constraints[tl.index]
        val frames = tl.frames
        val curves = tl.curves
        val n = frames.size
        var rm: Double
        var tm: Double
        var sm: Double
        var hm: Double
        if (time < frames[0].time) {
            rm = frames[0].f1
            tm = frames[0].f2
            sm = frames[0].f3
            hm = frames[0].f4
        } else if (time >= frames[n - 1].time) {
            rm = frames[n - 1].f1
            tm = frames[n - 1].f2
            sm = frames[n - 1].f3
            hm = frames[n - 1].f4
        } else {
            val ts = _frame_times(frames) { it.time }
            val frame = _binary_search(ts, time, 1)
            val fi = frame - 1
            val f0 = frames[fi]
            val f1 = frames[fi + 1]
            val frame_time = f0.time
            val next_time = f1.time
            var percent = if (next_time > frame_time) (time - frame_time) / (next_time - frame_time) else 0.0
            val curve = if (fi < curves.size) curves[fi] else null
            percent = curve_percent(curve, percent)
            rm = _lerp(f0.f1, f1.f1, percent)
            tm = _lerp(f0.f2, f1.f2, percent)
            sm = _lerp(f0.f3, f1.f3, percent)
            hm = _lerp(f0.f4, f1.f4, percent)
        }
        c.rotate_mix += (rm - c.rotate_mix) * alpha
        c.translate_mix += (tm - c.translate_mix) * alpha
        c.scale_mix += (sm - c.scale_mix) * alpha
        c.shear_mix += (hm - c.shear_mix) * alpha
    }
}

fun apply_path_timelines(animation: Animation, skeleton: Skeleton, time: Double, alpha: Double = 1.0) {
    for (tl in animation.path_timelines) {
        val c = skeleton.path_constraints[tl.index]
        if (tl.type == 2) { // mix
            val frames = tl.mix_frames
            val n = frames.size
            var m1: Double
            var m2: Double
            if (time < frames[0].time) {
                m1 = frames[0].m1
                m2 = frames[0].m2
            } else if (time >= frames[n - 1].time) {
                m1 = frames[n - 1].m1
                m2 = frames[n - 1].m2
            } else {
                val ts = _frame_times(frames) { it.time }
                val frame = _binary_search(ts, time, 1)
                val fi = frame - 1
                val frame_time = frames[fi].time
                val next_time = frames[fi + 1].time
                var percent = if (next_time > frame_time) (time - frame_time) / (next_time - frame_time) else 0.0
                percent = curve_percent(null, percent) // 路径曲线在 loader 读弃 → 线性
                m1 = _lerp(frames[fi].m1, frames[fi + 1].m1, percent)
                m2 = _lerp(frames[fi].m2, frames[fi + 1].m2, percent)
            }
            c.rotate_mix += (m1 - c.rotate_mix) * alpha
            c.translate_mix += (m2 - c.translate_mix) * alpha
        } else { // position / spacing
            val frames = tl.frames
            val n = frames.size
            var v: Double
            if (time < frames[0].time) {
                v = frames[0].value
            } else if (time >= frames[n - 1].time) {
                v = frames[n - 1].value
            } else {
                val ts = _frame_times(frames) { it.time }
                val frame = _binary_search(ts, time, 1)
                val fi = frame - 1
                val frame_time = frames[fi].time
                val next_time = frames[fi + 1].time
                var percent = if (next_time > frame_time) (time - frame_time) / (next_time - frame_time) else 0.0
                percent = curve_percent(null, percent) // 路径曲线在 loader 读弃 → 线性
                v = _lerp(frames[fi].value, frames[fi + 1].value, percent)
            }
            if (tl.type == 0) {
                c.position += (v - c.position) * alpha
            } else {
                c.spacing += (v - c.spacing) * alpha
            }
        }
    }
}

fun reset_constraints(skeleton: Skeleton) {
    for (c in skeleton.ik_constraints) {
        c.mix = c.data.mix
        c.softness = c.data.softness
        c.bend_direction = c.data.bend_direction
        c.compress = c.data.compress
        c.stretch = c.data.stretch
        c.uniform = c.data.uniform
    }
    for (c in skeleton.transform_constraints) {
        c.rotate_mix = c.data.rotate_mix
        c.translate_mix = c.data.translate_mix
        c.scale_mix = c.data.scale_mix
        c.shear_mix = c.data.shear_mix
    }
    for (c in skeleton.path_constraints) {
        c.position = c.data.position
        c.spacing = c.data.spacing
        c.rotate_mix = c.data.rotate_mix
        c.translate_mix = c.data.translate_mix
    }
}

// 应用动画全部时间线（JS blend=first + direction=mixIn 语义，alpha=1 时等价）
fun apply_animation(animation: Animation, skeleton: Skeleton, time: Double, loop: Boolean, alpha: Double = 1.0) {
    var t = time
    val duration = animation.duration
    if (loop && duration > 0) {
        t %= duration
    }
    reset_constraints(skeleton)
    for (tl in animation.bone_timelines) {
        if (tl.type == 0) { // rotate
            apply_rotate(tl, skeleton, t, alpha)
        } else if (tl.type == 1) { // translate
            apply_translate(tl, skeleton, t, alpha)
        } else { // scale / shear
            apply_scale_shear(tl, skeleton, t, alpha, tl.type == 2)
        }
    }
    for (tl in animation.slot_timelines) {
        if (tl.type == SLOT_ATTACHMENT) {
            apply_attachment(tl, skeleton, t, alpha)
        } else {
            apply_color(tl, skeleton, t, alpha)
        }
    }
    for (tl in animation.deform_timelines) {
        apply_deform(tl, skeleton, t, alpha)
    }
    for (tl in animation.draw_order_timelines) {
        apply_draw_order(tl, skeleton, t)
    }
    apply_ik_timelines(animation, skeleton, t, alpha)
    apply_transform_timelines(animation, skeleton, t, alpha)
    apply_path_timelines(animation, skeleton, t, alpha)
}
