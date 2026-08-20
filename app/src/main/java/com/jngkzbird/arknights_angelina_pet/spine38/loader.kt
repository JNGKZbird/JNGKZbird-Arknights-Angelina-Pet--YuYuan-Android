package com.jngkzbird.arknights_angelina_pet.spine38

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Spine 3.8 binary skeleton loader — ported from spine38/loader.ets（spine-ts 3.8 SkeletonBinary）。
 * spine 二进制全大端；时间线为类型化类；时间线 type 用数值常量（分节独立枚举）。
 */

// ── 枚举 ──────────────────────────────────────────────
const val ATTACHMENT_REGION = 0
const val ATTACHMENT_BOUNDING_BOX = 1
const val ATTACHMENT_MESH = 2
const val ATTACHMENT_LINKED_MESH = 3
const val ATTACHMENT_PATH = 4
const val ATTACHMENT_POINT = 5
const val ATTACHMENT_CLIPPING = 6

const val BLEND_NORMAL = 0
const val BLEND_ADDITIVE = 1
const val BLEND_MULTIPLY = 2
const val BLEND_SCREEN = 3

const val TRANSFORM_NORMAL = 0
const val TRANSFORM_ONLY_TRANSLATION = 1
const val TRANSFORM_NO_ROTATION_OR_REFLECTION = 2
const val TRANSFORM_NO_SCALE = 3
const val TRANSFORM_NO_SCALE_OR_REFLECTION = 4

const val POSITION_FIXED = 0
const val POSITION_PERCENT = 1

const val SPACING_LENGTH = 0
const val SPACING_FIXED = 1
const val SPACING_PERCENT = 2

const val ROTATE_TANGENT = 0
const val ROTATE_CHAIN = 1
const val ROTATE_CHAIN_SCALE = 2

// 槽位时间线类型（分节独立枚举）
const val SLOT_ATTACHMENT = 0
const val SLOT_COLOR = 1
const val SLOT_TWO_COLOR = 2
// 骨骼节：0=rotate 1=translate 2=scale 3=shear（直接用数值）
// 路径节：0=position 1=spacing 2=mix（直接用数值）

// ── 附件基类 ──────────────────────────────────────────
open class Attachment(val name: String)

open class VertexAttachment(name: String) : Attachment(name) {
    var bones: IntArray? = null
    var vertices: DoubleArray? = null
    var world_vertices_length: Int = 0
}

class RegionAttachment(name: String) : Attachment(name) {
    var path: String? = null
    var x: Double = 0.0
    var y: Double = 0.0
    var scale_x: Double = 1.0
    var scale_y: Double = 1.0
    var rotation: Double = 0.0
    var width: Double = 0.0
    var height: Double = 0.0
    var color: Color = Color()
    var region: AtlasRegion? = null
    var offset: DoubleArray = DoubleArray(8)

    // 角点顺序：BL, TL, TR, BR（JS OX1..OX4）
    fun update_offset() {
        val r = region!!
        val ow = if (r.original_width != 0) r.original_width else r.width
        val oh = if (r.original_height != 0) r.original_height else r.height
        val region_scale_x = width / ow * scale_x
        val region_scale_y = height / oh * scale_y
        val local_x = -width / 2.0 * scale_x + r.offset_x * region_scale_x
        val local_y = -height / 2.0 * scale_y + r.offset_y * region_scale_y
        val local_x2 = local_x + r.width * region_scale_x
        val local_y2 = local_y + r.height * region_scale_y
        val radians = deg_to_rad(rotation)
        val cos = kotlin.math.cos(radians)
        val sin = kotlin.math.sin(radians)
        val local_x_cos = local_x * cos + x
        val local_x_sin = local_x * sin
        val local_y_cos = local_y * cos + y
        val local_y_sin = local_y * sin
        val local_x2_cos = local_x2 * cos + x
        val local_x2_sin = local_x2 * sin
        val local_y2_cos = local_y2 * cos + y
        val local_y2_sin = local_y2 * sin
        offset[0] = local_x_cos - local_y_sin
        offset[1] = local_y_cos + local_x_sin
        offset[2] = local_x_cos - local_y2_sin
        offset[3] = local_y2_cos + local_x_sin
        offset[4] = local_x2_cos - local_y2_sin
        offset[5] = local_y2_cos + local_x2_sin
        offset[6] = local_x2_cos - local_y_sin
        offset[7] = local_y_cos + local_x2_sin
    }
}

class MeshAttachment(name: String) : VertexAttachment(name) {
    var path: String? = null
    var color: Color = Color()
    var triangles: IntArray? = null
    var region_uvs: DoubleArray? = null
    var uvs: DoubleArray? = null
    var hull_length: Int = 0
    var edges: IntArray? = null
    var width: Double = 0.0
    var height: Double = 0.0
    var region: AtlasRegion? = null
    var parent_mesh: Attachment? = null
    var deform_attachment: Attachment? = null
    var base_vertices: DoubleArray? = null // setup 恢复用

    // 二进制里 region_uvs 是相对区域原始空间的归一化坐标（0~1）
    // 坑（勿重踩）：ArkTS number 全浮点，Kotlin Int 除法会截断（ow/tw=0）→ UV 全塌缩到区域角点
    fun update_uvs() {
        val ruvs = region_uvs ?: return
        if (uvs == null || uvs!!.size != ruvs.size) {
            uvs = DoubleArray(ruvs.size)
        }
        val region = this.region ?: return
        val tw = (if (region.page_width != 0) region.page_width else 1).toDouble()
        val th = (if (region.page_height != 0) region.page_height else 1).toDouble()
        var u = region.u
        var v = region.v
        val out = uvs!!
        val ow = if (region.original_width != 0) region.original_width else region.width
        val oh = if (region.original_height != 0) region.original_height else region.height
        if (region.degrees == 90) {
            u -= (oh - region.offset_y - region.height) / tw
            v -= (ow - region.offset_x - region.width) / th
            val width = oh / tw
            val height = ow / th
            var i = 0
            while (i < ruvs.size) {
                out[i] = u + ruvs[i + 1] * width
                out[i + 1] = v + (1.0 - ruvs[i]) * height
                i += 2
            }
        } else if (region.degrees == 180) {
            u -= (ow - region.offset_x - region.width) / tw
            v -= region.offset_y / th
            val width = ow / tw
            val height = oh / th
            var i = 0
            while (i < ruvs.size) {
                out[i] = u + (1.0 - ruvs[i]) * width
                out[i + 1] = v + (1.0 - ruvs[i + 1]) * height
                i += 2
            }
        } else if (region.degrees == 270) {
            u -= region.offset_y / tw
            v -= region.offset_x / th
            val width = oh / tw
            val height = ow / th
            var i = 0
            while (i < ruvs.size) {
                out[i] = u + (1.0 - ruvs[i + 1]) * width
                out[i + 1] = v + ruvs[i] * height
                i += 2
            }
        } else {
            u -= region.offset_x / tw
            v -= (oh - region.offset_y - region.height) / th
            val width = ow / tw
            val height = oh / th
            var i = 0
            while (i < ruvs.size) {
                out[i] = u + ruvs[i] * width
                out[i + 1] = v + ruvs[i + 1] * height
                i += 2
            }
        }
    }
}

class BoundingBoxAttachment(name: String) : VertexAttachment(name) {
    var color: Color = Color()
}

class PathAttachment(name: String) : VertexAttachment(name) {
    var closed: Boolean = false
    var constant_speed: Boolean = false
    var lengths: DoubleArray? = null
    var color: Color = Color()
}

class PointAttachment(name: String) : Attachment(name) {
    var x: Double = 0.0
    var y: Double = 0.0
    var rotation: Double = 0.0
    var color: Color = Color()
}

class ClippingAttachment(name: String) : VertexAttachment(name) {
    var end_slot: SlotData? = null
    var color: Color = Color()
}

// ── 图集区域（由 atlas.kt 解析填充） ──
class AtlasRegion {
    var page: Any? = null
    var name: String = ""
    var u: Double = 0.0
    var v: Double = 0.0
    var u2: Double = 0.0
    var v2: Double = 0.0
    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0
    var original_width: Int = 0
    var original_height: Int = 0
    var offset_x: Int = 0
    var offset_y: Int = 0
    var page_width: Int = 0
    var page_height: Int = 0
    var degrees: Int = 0
    var rotate: Boolean = false
}

// ── 数据类 ─────────────────────────────────────────────
class BoneData(val index: Int, val name: String, val parent: BoneData?) {
    var length: Double = 0.0
    var x: Double = 0.0
    var y: Double = 0.0
    var rotation: Double = 0.0
    var scale_x: Double = 1.0
    var scale_y: Double = 1.0
    var shear_x: Double = 0.0
    var shear_y: Double = 0.0
    var transform_mode: Int = TRANSFORM_NORMAL
    var skin_required: Boolean = false
    var color: Color = Color()
}

class SlotData(val index: Int, val name: String, val bone_data: BoneData) {
    var color: Color = Color()
    var dark_color: Color? = null
    var attachment_name: String? = null
    var blend_mode: Int = BLEND_NORMAL
}

class IkConstraintData(val name: String) {
    var order: Int = 0
    var skin_required: Boolean = false
    var bones: MutableList<BoneData> = mutableListOf()
    var target: BoneData? = null
    var mix: Double = 1.0
    var softness: Double = 0.0
    var bend_direction: Int = 1
    var compress: Boolean = false
    var stretch: Boolean = false
    var uniform: Boolean = false
}

class TransformConstraintData(val name: String) {
    var order: Int = 0
    var skin_required: Boolean = false
    var bones: MutableList<BoneData> = mutableListOf()
    var target: BoneData? = null
    var local: Boolean = false
    var relative: Boolean = false
    var offset_rotation: Double = 0.0
    var offset_x: Double = 0.0
    var offset_y: Double = 0.0
    var offset_scale_x: Double = 1.0
    var offset_scale_y: Double = 1.0
    var offset_shear_y: Double = 0.0
    var rotate_mix: Double = 1.0
    var translate_mix: Double = 1.0
    var scale_mix: Double = 1.0
    var shear_mix: Double = 1.0
}

class PathConstraintData(val name: String) {
    var order: Int = 0
    var skin_required: Boolean = false
    var bones: MutableList<BoneData> = mutableListOf()
    var target: SlotData? = null
    var position_mode: Int = POSITION_FIXED
    var spacing_mode: Int = SPACING_LENGTH
    var rotate_mode: Int = ROTATE_TANGENT
    var offset_rotation: Double = 0.0
    var position: Double = 0.0
    var spacing: Double = 0.0
    var rotate_mix: Double = 1.0
    var translate_mix: Double = 1.0
}

class EventData(val name: String) {
    var int_value: Int = 0
    var float_value: Double = 0.0
    var string_value: String? = null
    var audio_path: String? = null
    var volume: Double = 0.0
    var balance: Double = 0.0
}

// ── 附件加载器接口 ─────────────────────────────────────
abstract class AttachmentLoader {
    abstract fun new_region_attachment(skin: Skin, name: String, path: String): RegionAttachment?
    abstract fun new_mesh_attachment(skin: Skin, name: String, path: String): MeshAttachment?
    abstract fun new_bounding_box_attachment(skin: Skin, name: String): BoundingBoxAttachment?
    abstract fun new_path_attachment(skin: Skin, name: String): PathAttachment?
    abstract fun new_point_attachment(skin: Skin, name: String): PointAttachment?
    abstract fun new_clipping_attachment(skin: Skin, name: String): ClippingAttachment?
}

// ── 皮肤 ──────────────────────────────────────────────
class Skin(val name: String) {
    // slot_index -> name -> attachment
    val attachments: MutableMap<Int, MutableMap<String, Attachment>> = mutableMapOf()

    fun set_attachment(slot_index: Int, name: String, attachment: Attachment) {
        var inner = attachments[slot_index]
        if (inner == null) {
            inner = mutableMapOf()
            attachments[slot_index] = inner
        }
        inner[name] = attachment
    }

    fun get_attachment(slot_index: Int, name: String): Attachment? {
        val inner = attachments[slot_index] ?: return null
        return inner[name]
    }
}

// ── 骨骼数据 ──────────────────────────────────────────
class SkeletonData {
    var name: String = ""
    var bones: MutableList<BoneData> = mutableListOf()
    var slots: MutableList<SlotData> = mutableListOf()
    var skins: MutableList<Skin> = mutableListOf()
    var default_skin: Skin? = null
    var events: MutableList<EventData> = mutableListOf()
    var animations: MutableList<Animation> = mutableListOf()
    var ik_constraints: MutableList<IkConstraintData> = mutableListOf()
    var transform_constraints: MutableList<TransformConstraintData> = mutableListOf()
    var path_constraints: MutableList<PathConstraintData> = mutableListOf()
    var x: Double = 0.0
    var y: Double = 0.0
    var width: Double = 0.0
    var height: Double = 0.0
    var version: String? = null
    var hash: String? = null
    var fps: Double = 0.0

    fun find_bone(name: String): BoneData? = bones.firstOrNull { it.name == name }

    fun find_skin(name: String): Skin? = skins.firstOrNull { it.name == name }

    fun find_event(name: String): EventData? = events.firstOrNull { it.name == name }

    fun find_animation(name: String): Animation? = animations.firstOrNull { it.name == name }
}

// ── 二进制读取器（spine 二进制全大端） ──────────────────
class BinaryInput(data: ByteArray) {
    private val buf: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
    val strings: MutableList<String?> = mutableListOf()

    fun read_byte(): Int = buf.get().toInt() and 0xFF

    fun read_sbyte(): Int = buf.get().toInt()

    fun read_boolean(): Boolean = read_byte() != 0

    fun read_short(): Int = buf.short.toInt()

    // varint：高位续读；32 位有符号语义（如绘制顺序 -44 偏移）
    fun read_int(optimize_positive: Boolean = false): Int {
        var b = read_byte()
        var result = b and 0x7F
        if ((b and 0x80) != 0) {
            b = read_byte()
            result = result or ((b and 0x7F) shl 7)
            if ((b and 0x80) != 0) {
                b = read_byte()
                result = result or ((b and 0x7F) shl 14)
                if ((b and 0x80) != 0) {
                    b = read_byte()
                    result = result or ((b and 0x7F) shl 21)
                    if ((b and 0x80) != 0) {
                        b = read_byte()
                        result = result or ((b and 0x7F) shl 28)
                    }
                }
            }
        }
        return if (optimize_positive) result else (result shr 1) xor -(result and 1)
    }

    fun read_int32(): Int = buf.int

    fun read_float(): Float = buf.float

    fun read_string(): String? {
        var n = read_int(true)
        if (n == 0) {
            return null
        }
        if (n == 1) {
            return ""
        }
        n -= 1
        // latin-1：每字节一个字符
        val chars = CharArray(n)
        for (i in 0 until n) {
            chars[i] = (buf.get().toInt() and 0xFF).toChar()
        }
        return String(chars)
    }

    fun read_string_ref(): String? {
        var idx = read_int(true)
        if (idx == 0) {
            return null
        }
        idx -= 1
        return if (idx < strings.size) strings[idx] else null
    }
}

// ── 时间线数据 ────────────────────────────────────────
class Curve {
    var type: Int = 0 // 0=linear 1=stepped 2=bezier
    var cx1: Double = 0.0
    var cy1: Double = 0.0
    var cx2: Double = 0.0
    var cy2: Double = 0.0
}

class SlotAttachmentFrame(val time: Double, val name: String?)

class SlotColorFrame(val time: Double, val color: Color, val dark: Color?)

class SlotTimeline {
    var type: Int = 0 // SLOT_ATTACHMENT / SLOT_COLOR / SLOT_TWO_COLOR
    var slot: Int = 0
    var attachment_frames: MutableList<SlotAttachmentFrame> = mutableListOf()
    var color_frames: MutableList<SlotColorFrame> = mutableListOf()
}

class BoneFrame(val time: Double, val f1: Double, val f2: Double) {
    // rotate 只用 f1；translate/scale/shear 用 f1,f2
}

class BoneTimeline {
    var type: Int = 0 // 0=rotate 1=translate 2=scale 3=shear
    var bone: Int = 0
    var frames: MutableList<BoneFrame> = mutableListOf()
    var curves: MutableList<Curve> = mutableListOf()
}

class IkFrame(
    val time: Double,
    val mix: Double,
    val softness: Double,
    val bend: Int, // 有符号字节（勿按无符号读）
    val compress: Boolean,
    val stretch: Boolean
)

class IkTimeline {
    var index: Int = 0
    var frames: MutableList<IkFrame> = mutableListOf()
    var curves: MutableList<Curve> = mutableListOf()
}

class TransformFrame(val time: Double, val f1: Double, val f2: Double, val f3: Double, val f4: Double)

class TransformTimeline {
    var index: Int = 0
    var frames: MutableList<TransformFrame> = mutableListOf()
    var curves: MutableList<Curve> = mutableListOf()
}

class PathFrame(val time: Double, val value: Double)

class PathMixFrame(val time: Double, val m1: Double, val m2: Double)

class PathTimeline {
    var type: Int = 0 // 0=position 1=spacing 2=mix
    var index: Int = 0
    var frames: MutableList<PathFrame> = mutableListOf()
    var mix_frames: MutableList<PathMixFrame> = mutableListOf()
}

class DeformFrame(val time: Double, val values: DoubleArray)

class DeformTimeline {
    var skin: Skin? = null
    var slot: Int = 0
    var attachment: VertexAttachment? = null
    var frames: MutableList<DeformFrame> = mutableListOf()
}

class DrawOrderOffset(val slot: Int, val offset: Int)

class DrawOrderFrame(val time: Double, val offsets: List<DrawOrderOffset>)

class DrawOrderTimeline {
    var frames: MutableList<DrawOrderFrame> = mutableListOf()
}

class EventFrame(val time: Double, val event: EventData)

class EventTimeline {
    var frames: MutableList<EventFrame> = mutableListOf()
}

// ── 动画 ──────────────────────────────────────────────
class Animation(val name: String) {
    var duration: Double = 0.0
    var slot_timelines: MutableList<SlotTimeline> = mutableListOf()
    var bone_timelines: MutableList<BoneTimeline> = mutableListOf()
    var ik_timelines: MutableList<IkTimeline> = mutableListOf()
    var transform_timelines: MutableList<TransformTimeline> = mutableListOf()
    var path_timelines: MutableList<PathTimeline> = mutableListOf()
    var deform_timelines: MutableList<DeformTimeline> = mutableListOf()
    var event_timelines: MutableList<EventTimeline> = mutableListOf()
    var draw_order_timelines: MutableList<DrawOrderTimeline> = mutableListOf()
}

// ── 内部辅助类 ────────────────────────────────────────
internal class VerticesResult {
    var vertices: DoubleArray = DoubleArray(0)
    var bones: IntArray? = null
}

internal class LinkedMesh(
    val mesh: MeshAttachment,
    val skin_name: String?,
    val slot_index: Int,
    val parent: String,
    val inherit_deform: Boolean
)

// ── 骨骼加载器 ────────────────────────────────────────
class SkeletonBinary(private val attachment_loader: AttachmentLoader) {
    var scale: Double = 1.0
    private val linked_meshes: MutableList<LinkedMesh> = mutableListOf()

    fun read_skeleton_data(data: ByteArray): SkeletonData {
        val scale = this.scale
        val sd = SkeletonData()
        val inp = BinaryInput(data)
        sd.hash = inp.read_string()
        sd.version = inp.read_string()
        if (sd.version == "3.8.75") {
            throw IllegalStateException("Unsupported skeleton data, please export with a newer version of Spine.")
        }
        sd.x = inp.read_float().toDouble()
        sd.y = inp.read_float().toDouble()
        sd.width = inp.read_float().toDouble()
        sd.height = inp.read_float().toDouble()
        val nonessential = inp.read_boolean()
        if (nonessential) {
            sd.fps = inp.read_float().toDouble()
            inp.read_string() // images path
            inp.read_string() // audio path
        }

        var n = inp.read_int(true)
        for (i in 0 until n) {
            inp.strings.add(inp.read_string())
        }

        n = inp.read_int(true)
        for (i in 0 until n) {
            val name = inp.read_string()!!
            val parent = if (i == 0) null else sd.bones[inp.read_int(true)]
            val bd = BoneData(i, name, parent)
            bd.rotation = inp.read_float().toDouble()
            bd.x = inp.read_float().toDouble() * scale
            bd.y = inp.read_float().toDouble() * scale
            bd.scale_x = inp.read_float().toDouble()
            bd.scale_y = inp.read_float().toDouble()
            bd.shear_x = inp.read_float().toDouble()
            bd.shear_y = inp.read_float().toDouble()
            bd.length = inp.read_float().toDouble() * scale
            bd.transform_mode = inp.read_int(true)
            bd.skin_required = inp.read_boolean()
            if (nonessential) {
                Color.rgba8888(bd.color, inp.read_int32())
            }
            sd.bones.add(bd)
        }

        n = inp.read_int(true)
        for (i in 0 until n) {
            val slot_name = inp.read_string()!!
            val bone_data = sd.bones[inp.read_int(true)]
            val sld = SlotData(i, slot_name, bone_data)
            Color.rgba8888(sld.color, inp.read_int32())
            val dark = inp.read_int32()
            if (dark != -1) {
                sld.dark_color = Color()
                Color.rgb888(sld.dark_color!!, dark)
            }
            sld.attachment_name = inp.read_string_ref()
            sld.blend_mode = inp.read_int(true)
            sd.slots.add(sld)
        }

        n = inp.read_int(true)
        for (i in 0 until n) {
            val d = IkConstraintData(inp.read_string()!!)
            d.order = inp.read_int(true)
            d.skin_required = inp.read_boolean()
            var nn = inp.read_int(true)
            for (j in 0 until nn) {
                d.bones.add(sd.bones[inp.read_int(true)])
            }
            d.target = sd.bones[inp.read_int(true)]
            d.mix = inp.read_float().toDouble()
            d.softness = inp.read_float().toDouble() * scale
            d.bend_direction = inp.read_sbyte() // 有符号字节（255≠-1）
            d.compress = inp.read_boolean()
            d.stretch = inp.read_boolean()
            d.uniform = inp.read_boolean()
            sd.ik_constraints.add(d)
        }

        n = inp.read_int(true)
        for (i in 0 until n) {
            val d = TransformConstraintData(inp.read_string()!!)
            d.order = inp.read_int(true)
            d.skin_required = inp.read_boolean()
            var nn = inp.read_int(true)
            for (j in 0 until nn) {
                d.bones.add(sd.bones[inp.read_int(true)])
            }
            d.target = sd.bones[inp.read_int(true)]
            d.local = inp.read_boolean()
            d.relative = inp.read_boolean()
            d.offset_rotation = inp.read_float().toDouble()
            d.offset_x = inp.read_float().toDouble() * scale
            d.offset_y = inp.read_float().toDouble() * scale
            d.offset_scale_x = inp.read_float().toDouble()
            d.offset_scale_y = inp.read_float().toDouble()
            d.offset_shear_y = inp.read_float().toDouble()
            d.rotate_mix = inp.read_float().toDouble()
            d.translate_mix = inp.read_float().toDouble()
            d.scale_mix = inp.read_float().toDouble()
            d.shear_mix = inp.read_float().toDouble()
            sd.transform_constraints.add(d)
        }

        n = inp.read_int(true)
        for (i in 0 until n) {
            val d = PathConstraintData(inp.read_string()!!)
            d.order = inp.read_int(true)
            d.skin_required = inp.read_boolean()
            var nn = inp.read_int(true)
            for (j in 0 until nn) {
                d.bones.add(sd.bones[inp.read_int(true)])
            }
            d.target = sd.slots[inp.read_int(true)]
            d.position_mode = inp.read_int(true)
            d.spacing_mode = inp.read_int(true)
            d.rotate_mode = inp.read_int(true)
            d.offset_rotation = inp.read_float().toDouble()
            d.position = inp.read_float().toDouble()
            if (d.position_mode == POSITION_FIXED) {
                d.position *= scale
            }
            d.spacing = inp.read_float().toDouble()
            if (d.spacing_mode == SPACING_LENGTH || d.spacing_mode == SPACING_FIXED) {
                d.spacing *= scale
            }
            d.rotate_mix = inp.read_float().toDouble()
            d.translate_mix = inp.read_float().toDouble()
            sd.path_constraints.add(d)
        }

        val default_skin = _read_skin(inp, sd, true, nonessential)
        if (default_skin != null) {
            sd.default_skin = default_skin
            sd.skins.add(default_skin)
        }

        n = inp.read_int(true)
        for (i in 0 until n) {
            val s = _read_skin(inp, sd, false, nonessential)
            if (s != null) {
                sd.skins.add(s)
            }
        }

        // linked meshes
        for (lm in linked_meshes) {
            val skin = (if (lm.skin_name == null) sd.default_skin else sd.find_skin(lm.skin_name))
                ?: throw IllegalStateException("Skin not found: ${lm.skin_name}")
            val parent = skin.get_attachment(lm.slot_index, lm.parent)
                ?: throw IllegalStateException("Parent mesh not found: ${lm.parent}")
            lm.mesh.deform_attachment = if (lm.inherit_deform) parent else lm.mesh
            lm.mesh.parent_mesh = parent
            lm.mesh.update_uvs()
        }
        linked_meshes.clear()

        n = inp.read_int(true)
        for (i in 0 until n) {
            val d = EventData(inp.read_string_ref()!!)
            d.int_value = inp.read_int(false)
            d.float_value = inp.read_float().toDouble()
            d.string_value = inp.read_string()
            d.audio_path = inp.read_string()
            if (d.audio_path != null) {
                d.volume = inp.read_float().toDouble()
                d.balance = inp.read_float().toDouble()
            }
            sd.events.add(d)
        }

        n = inp.read_int(true)
        for (i in 0 until n) {
            sd.animations.add(_read_animation(inp, inp.read_string()!!, sd))
        }
        return sd
    }

    private fun _read_skin(inp: BinaryInput, sd: SkeletonData, is_default: Boolean, nonessential: Boolean): Skin? {
        var skin: Skin?
        var slot_count: Int
        if (is_default) {
            slot_count = inp.read_int(true)
            if (slot_count == 0) {
                return null
            }
            skin = Skin("default")
        } else {
            skin = Skin(inp.read_string_ref()!!)
            var nb = inp.read_int(true)
            for (i in 0 until nb) {
                inp.read_int(true) // bone index (skin.bones not needed)
            }
            var nc = inp.read_int(true)
            for (i in 0 until nc) {
                inp.read_int(true) // ik constraint
            }
            nc = inp.read_int(true)
            for (i in 0 until nc) {
                inp.read_int(true) // transform constraint
            }
            nc = inp.read_int(true)
            for (i in 0 until nc) {
                inp.read_int(true) // path constraint
            }
            slot_count = inp.read_int(true)
        }
        val s = skin!!
        for (i in 0 until slot_count) {
            val slot_index = inp.read_int(true)
            val nn = inp.read_int(true)
            for (j in 0 until nn) {
                val name = inp.read_string_ref()
                val attachment = _read_attachment(inp, sd, s, slot_index, name, nonessential)
                if (attachment != null) {
                    s.set_attachment(slot_index, name!!, attachment)
                }
            }
        }
        return s
    }

    private fun _read_attachment(
        inp: BinaryInput, sd: SkeletonData, skin: Skin, slot_index: Int,
        attachment_name: String?, nonessential: Boolean
    ): Attachment? {
        val scale = this.scale
        var name = inp.read_string_ref()
        if (name == null) {
            name = attachment_name
        }
        val type_index = inp.read_byte()
        val loader = attachment_loader
        when (type_index) {
            ATTACHMENT_REGION -> {
                var path = inp.read_string_ref()
                val rotation = inp.read_float().toDouble()
                val x = inp.read_float().toDouble()
                val y = inp.read_float().toDouble()
                val sx = inp.read_float().toDouble()
                val sy = inp.read_float().toDouble()
                val width = inp.read_float().toDouble()
                val height = inp.read_float().toDouble()
                val color = inp.read_int32()
                if (path == null) {
                    path = name
                }
                val a = loader.new_region_attachment(skin, name!!, path!!) ?: return null
                a.path = path
                a.x = x * scale
                a.y = y * scale
                a.scale_x = sx
                a.scale_y = sy
                a.rotation = rotation
                a.width = width * scale
                a.height = height * scale
                Color.rgba8888(a.color, color)
                a.update_offset()
                return a
            }

            ATTACHMENT_BOUNDING_BOX -> {
                val vertex_count = inp.read_int(true)
                val vr = _read_vertices(inp, vertex_count)
                val color = if (nonessential) inp.read_int32() else 0
                val a = loader.new_bounding_box_attachment(skin, name!!) ?: return null
                a.world_vertices_length = vertex_count shl 1
                a.vertices = vr.vertices
                a.bones = vr.bones
                if (nonessential) {
                    Color.rgba8888(a.color, color)
                }
                return a
            }

            ATTACHMENT_MESH -> {
                var path = inp.read_string_ref()
                val color = inp.read_int32()
                val vertex_count = inp.read_int(true)
                val uvs = _read_float_array(inp, vertex_count shl 1, 1.0)
                val triangles = _read_short_array(inp)
                val vr = _read_vertices(inp, vertex_count)
                val hull_length = inp.read_int(true)
                var edges: IntArray? = null
                var width = 0.0
                var height = 0.0
                if (nonessential) {
                    edges = _read_short_array(inp)
                    width = inp.read_float().toDouble()
                    height = inp.read_float().toDouble()
                }
                if (path == null) {
                    path = name
                }
                val a = loader.new_mesh_attachment(skin, name!!, path!!) ?: return null
                a.path = path
                Color.rgba8888(a.color, color)
                a.bones = vr.bones
                a.vertices = vr.vertices
                a.base_vertices = vr.vertices.copyOf()
                a.world_vertices_length = vertex_count shl 1
                a.triangles = triangles
                a.region_uvs = uvs
                a.update_uvs()
                a.hull_length = hull_length shl 1
                if (nonessential) {
                    a.edges = edges
                    a.width = width * scale
                    a.height = height * scale
                }
                return a
            }

            ATTACHMENT_LINKED_MESH -> {
                var path = inp.read_string_ref()
                val color = inp.read_int32()
                val skin_name = inp.read_string_ref()
                val parent = inp.read_string_ref()
                val inherit_deform = inp.read_boolean()
                var width = 0.0
                var height = 0.0
                if (nonessential) {
                    width = inp.read_float().toDouble()
                    height = inp.read_float().toDouble()
                }
                if (path == null) {
                    path = name
                }
                val a = loader.new_mesh_attachment(skin, name!!, path!!) ?: return null
                a.path = path
                Color.rgba8888(a.color, color)
                if (nonessential) {
                    a.width = width * scale
                    a.height = height * scale
                }
                linked_meshes.add(LinkedMesh(a, skin_name, slot_index, parent!!, inherit_deform))
                return a
            }

            ATTACHMENT_PATH -> {
                val closed = inp.read_boolean()
                val constant_speed = inp.read_boolean()
                val vertex_count = inp.read_int(true)
                val vr = _read_vertices(inp, vertex_count)
                val lengths = ArrayList<Double>()
                val lc = vertex_count / 3
                for (i in 0 until lc) {
                    lengths.add(inp.read_float().toDouble() * scale)
                }
                val color = if (nonessential) inp.read_int32() else 0
                val a = loader.new_path_attachment(skin, name!!) ?: return null
                a.closed = closed
                a.constant_speed = constant_speed
                a.world_vertices_length = vertex_count shl 1
                a.vertices = vr.vertices
                a.bones = vr.bones
                a.lengths = lengths.toDoubleArray()
                if (nonessential) {
                    Color.rgba8888(a.color, color)
                }
                return a
            }

            ATTACHMENT_POINT -> {
                val rotation = inp.read_float().toDouble()
                val x = inp.read_float().toDouble()
                val y = inp.read_float().toDouble()
                val color = if (nonessential) inp.read_int32() else 0
                val a = loader.new_point_attachment(skin, name!!) ?: return null
                a.x = x * scale
                a.y = y * scale
                a.rotation = rotation
                if (nonessential) {
                    Color.rgba8888(a.color, color)
                }
                return a
            }

            ATTACHMENT_CLIPPING -> {
                val end_slot_index = inp.read_int(true)
                val vertex_count = inp.read_int(true)
                val vr = _read_vertices(inp, vertex_count)
                val color = if (nonessential) inp.read_int32() else 0
                val a = loader.new_clipping_attachment(skin, name!!) ?: return null
                a.end_slot = sd.slots[end_slot_index]
                a.world_vertices_length = vertex_count shl 1
                a.vertices = vr.vertices
                a.bones = vr.bones
                if (nonessential) {
                    Color.rgba8888(a.color, color)
                }
                return a
            }

            else -> return null
        }
    }

    private fun _read_vertices(inp: BinaryInput, vertex_count: Int): VerticesResult {
        val vertices_length = vertex_count shl 1
        val scale = this.scale
        if (!inp.read_boolean()) {
            val r = VerticesResult()
            r.vertices = _read_float_array(inp, vertices_length, scale)
            r.bones = null
            return r
        }
        val weights = ArrayList<Double>()
        val bones = ArrayList<Int>()
        for (i in 0 until vertex_count) {
            val bone_count = inp.read_int(true)
            bones.add(bone_count)
            for (j in 0 until bone_count) {
                bones.add(inp.read_int(true))
                weights.add(inp.read_float().toDouble() * scale)
                weights.add(inp.read_float().toDouble() * scale)
                weights.add(inp.read_float().toDouble())
            }
        }
        val r = VerticesResult()
        r.vertices = weights.toDoubleArray()
        r.bones = bones.toIntArray()
        return r
    }

    private fun _read_float_array(inp: BinaryInput, n: Int, scale: Double): DoubleArray {
        val arr = DoubleArray(n)
        if (scale == 1.0) {
            for (i in 0 until n) {
                arr[i] = inp.read_float().toDouble()
            }
        } else {
            for (i in 0 until n) {
                arr[i] = inp.read_float().toDouble() * scale
            }
        }
        return arr
    }

    private fun _read_short_array(inp: BinaryInput): IntArray {
        val n = inp.read_int(true)
        val arr = IntArray(n)
        for (i in 0 until n) {
            arr[i] = inp.read_short()
        }
        return arr
    }

    private fun _read_animation(inp: BinaryInput, name: String, sd: SkeletonData): Animation {
        val animation = Animation(name)
        val scale = this.scale
        var duration = 0.0

        // 槽位时间线
        var n = inp.read_int(true)
        for (i in 0 until n) {
            val slot_index = inp.read_int(true)
            val nn = inp.read_int(true)
            for (j in 0 until nn) {
                val timeline_type = inp.read_byte()
                val frame_count = inp.read_int(true)
                val tl = SlotTimeline()
                tl.type = timeline_type
                tl.slot = slot_index
                if (timeline_type == SLOT_ATTACHMENT) {
                    for (k in 0 until frame_count) {
                        val t = inp.read_float().toDouble()
                        tl.attachment_frames.add(SlotAttachmentFrame(t, inp.read_string_ref()))
                    }
                    animation.slot_timelines.add(tl)
                    duration = maxOf(duration, tl.attachment_frames[tl.attachment_frames.size - 1].time)
                } else if (timeline_type == SLOT_COLOR || timeline_type == SLOT_TWO_COLOR) {
                    for (k in 0 until frame_count) {
                        val t = inp.read_float().toDouble()
                        val c1 = Color()
                        Color.rgba8888(c1, inp.read_int32())
                        var c2: Color? = null
                        if (timeline_type == SLOT_TWO_COLOR) {
                            c2 = Color()
                            Color.rgb888(c2, inp.read_int32())
                        }
                        tl.color_frames.add(SlotColorFrame(t, c1, c2))
                        if (k < frame_count - 1) {
                            _read_curve(inp) // 读了即弃（槽位颜色用线性插值）
                        }
                    }
                    animation.slot_timelines.add(tl)
                    duration = maxOf(duration, tl.color_frames[tl.color_frames.size - 1].time)
                }
            }
        }

        // 骨骼时间线（类型枚举独立：0=rotate 1=translate 2=scale 3=shear）
        n = inp.read_int(true)
        for (i in 0 until n) {
            val bone_index = inp.read_int(true)
            val nn = inp.read_int(true)
            for (j in 0 until nn) {
                val timeline_type = inp.read_byte()
                val frame_count = inp.read_int(true)
                val tl = BoneTimeline()
                tl.type = timeline_type
                tl.bone = bone_index
                if (timeline_type == 0) { // rotate
                    for (k in 0 until frame_count) {
                        val t = inp.read_float().toDouble()
                        tl.frames.add(BoneFrame(t, inp.read_float().toDouble(), 0.0))
                        if (k < frame_count - 1) {
                            tl.curves.add(_read_curve(inp))
                        }
                    }
                    animation.bone_timelines.add(tl)
                    duration = maxOf(duration, tl.frames[tl.frames.size - 1].time)
                } else if (timeline_type == 1 || timeline_type == 2 || timeline_type == 3) { // translate / scale / shear
                    val tscale = if (timeline_type == 1) scale else 1.0
                    for (k in 0 until frame_count) {
                        val t = inp.read_float().toDouble()
                        tl.frames.add(BoneFrame(t, inp.read_float().toDouble() * tscale, inp.read_float().toDouble() * tscale))
                        if (k < frame_count - 1) {
                            tl.curves.add(_read_curve(inp))
                        }
                    }
                    animation.bone_timelines.add(tl)
                    duration = maxOf(duration, tl.frames[tl.frames.size - 1].time)
                }
            }
        }

        // IK 约束时间线
        n = inp.read_int(true)
        for (i in 0 until n) {
            val index = inp.read_int(true)
            val frame_count = inp.read_int(true)
            val tl = IkTimeline()
            tl.index = index
            for (k in 0 until frame_count) {
                val t = inp.read_float().toDouble()
                tl.frames.add(
                    IkFrame(
                        t, inp.read_float().toDouble(), inp.read_float().toDouble() * scale, inp.read_sbyte(),
                        inp.read_boolean(), inp.read_boolean()
                    )
                )
                if (k < frame_count - 1) {
                    tl.curves.add(_read_curve(inp))
                }
            }
            animation.ik_timelines.add(tl)
            duration = maxOf(duration, tl.frames[tl.frames.size - 1].time)
        }

        // 变换约束时间线
        n = inp.read_int(true)
        for (i in 0 until n) {
            val index = inp.read_int(true)
            val frame_count = inp.read_int(true)
            val tl = TransformTimeline()
            tl.index = index
            for (k in 0 until frame_count) {
                val t = inp.read_float().toDouble()
                tl.frames.add(
                    TransformFrame(
                        t, inp.read_float().toDouble(), inp.read_float().toDouble(),
                        inp.read_float().toDouble(), inp.read_float().toDouble()
                    )
                )
                if (k < frame_count - 1) {
                    tl.curves.add(_read_curve(inp))
                }
            }
            animation.transform_timelines.add(tl)
            duration = maxOf(duration, tl.frames[tl.frames.size - 1].time)
        }

        // 路径约束时间线（类型枚举独立：0=position 1=spacing 2=mix）
        n = inp.read_int(true)
        for (i in 0 until n) {
            val index = inp.read_int(true)
            val data = sd.path_constraints[index]
            val nn = inp.read_int(true)
            for (j in 0 until nn) {
                val timeline_type = inp.read_byte()
                val frame_count = inp.read_int(true)
                val tl = PathTimeline()
                tl.type = timeline_type
                tl.index = index
                if (timeline_type == 0 || timeline_type == 1) { // position / spacing
                    var tscale = 1.0
                    if (timeline_type == 1 && (data.spacing_mode == SPACING_LENGTH || data.spacing_mode == SPACING_FIXED)) {
                        tscale = scale
                    } else if (timeline_type == 0 && data.position_mode == POSITION_FIXED) {
                        tscale = scale
                    }
                    for (k in 0 until frame_count) {
                        val t = inp.read_float().toDouble()
                        tl.frames.add(PathFrame(t, inp.read_float().toDouble() * tscale))
                        if (k < frame_count - 1) {
                            _read_curve(inp) // 读了即弃
                        }
                    }
                    animation.path_timelines.add(tl)
                    duration = maxOf(duration, tl.frames[tl.frames.size - 1].time)
                } else if (timeline_type == 2) { // mix
                    for (k in 0 until frame_count) {
                        val t = inp.read_float().toDouble()
                        tl.mix_frames.add(PathMixFrame(t, inp.read_float().toDouble(), inp.read_float().toDouble()))
                        if (k < frame_count - 1) {
                            _read_curve(inp) // 读了即弃
                        }
                    }
                    animation.path_timelines.add(tl)
                    duration = maxOf(duration, tl.mix_frames[tl.mix_frames.size - 1].time)
                }
            }
        }

        // 变形时间线：skin → nn(插槽数) → slotIndex → nnn(附件数) → 附件
        n = inp.read_int(true)
        for (i in 0 until n) {
            val skin = sd.skins[inp.read_int(true)]
            val nn = inp.read_int(true)
            for (j in 0 until nn) {
                val slot_index = inp.read_int(true)
                val nnn = inp.read_int(true)
                for (k in 0 until nnn) {
                    val att_name = inp.read_string_ref()
                    val attachment = skin.get_attachment(slot_index, att_name!!) as? VertexAttachment
                    val weighted = attachment != null && attachment.bones != null
                    val vertices = attachment?.vertices
                    var deform_length = 0
                    if (vertices != null) {
                        deform_length = if (weighted) vertices.size / 3 * 2 else vertices.size
                    }
                    val frame_count = inp.read_int(true)
                    val tl = DeformTimeline()
                    tl.skin = skin
                    tl.slot = slot_index
                    tl.attachment = attachment
                    for (f in 0 until frame_count) {
                        val t = inp.read_float().toDouble()
                        var end = inp.read_int(true)
                        val deform: DoubleArray
                        if (end == 0) {
                            // 空变形：加权=全零，非加权=原顶点
                            deform = if (weighted) DoubleArray(deform_length) else vertices!!.copyOf()
                        } else {
                            deform = DoubleArray(deform_length)
                            val start = inp.read_int(true)
                            end += start
                            val limit = minOf(end, deform_length)
                            for (v in start until limit) {
                                deform[v] = inp.read_float().toDouble() * scale
                            }
                            if (!weighted) {
                                for (v in 0 until deform_length) {
                                    deform[v] += vertices!![v]
                                }
                            }
                        }
                        tl.frames.add(DeformFrame(t, deform))
                        if (f < frame_count - 1) {
                            _read_curve(inp) // 读了即弃
                        }
                    }
                    animation.deform_timelines.add(tl)
                    duration = maxOf(duration, tl.frames[tl.frames.size - 1].time)
                }
            }
        }

        // 绘制顺序时间线（在事件之前）：drawOrderCount = 帧数，单时间线
        // 每帧：time + offsetCount + 每偏移 (slotIndex, offset) 两个 varint
        val draw_order_count = inp.read_int(true)
        if (draw_order_count > 0) {
            val tl = DrawOrderTimeline()
            for (i in 0 until draw_order_count) {
                val t = inp.read_float().toDouble()
                val offset_count = inp.read_int(true)
                val offsets = ArrayList<DrawOrderOffset>()
                for (j in 0 until offset_count) {
                    val slot_i = inp.read_int(true)
                    val off = inp.read_int(true)
                    offsets.add(DrawOrderOffset(slot_i, off))
                }
                tl.frames.add(DrawOrderFrame(t, offsets))
            }
            animation.draw_order_timelines.add(tl)
            duration = maxOf(duration, tl.frames[tl.frames.size - 1].time)
        }

        // 事件时间线：eventCount = 帧数，单时间线；时间只读一次
        val event_count = inp.read_int(true)
        if (event_count > 0) {
            val tl = EventTimeline()
            for (i in 0 until event_count) {
                val t = inp.read_float().toDouble()
                val d = sd.events[inp.read_int(true)]
                val e = EventData(d.name)
                e.int_value = inp.read_int(false)
                e.float_value = inp.read_float().toDouble()
                if (inp.read_boolean()) {
                    e.string_value = inp.read_string()
                } else {
                    e.string_value = d.string_value
                }
                if (d.audio_path != null) {
                    e.volume = inp.read_float().toDouble()
                    e.balance = inp.read_float().toDouble()
                }
                tl.frames.add(EventFrame(t, e))
            }
            animation.event_timelines.add(tl)
            duration = maxOf(duration, tl.frames[tl.frames.size - 1].time)
        }

        animation.duration = duration
        return animation
    }

    // 曲线数据：0=线性 1=阶梯 或 (cx1,cy1,cx2,cy2) 单段贝塞尔
    private fun _read_curve(inp: BinaryInput): Curve {
        val curve_type = inp.read_byte()
        val c = Curve()
        c.type = curve_type
        if (curve_type == 2) {
            c.cx1 = inp.read_float().toDouble()
            c.cy1 = inp.read_float().toDouble()
            c.cx2 = inp.read_float().toDouble()
            c.cy2 = inp.read_float().toDouble()
        }
        return c
    }
}
