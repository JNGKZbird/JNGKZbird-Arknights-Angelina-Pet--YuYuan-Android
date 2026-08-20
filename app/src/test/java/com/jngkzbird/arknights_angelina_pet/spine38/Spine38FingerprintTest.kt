package com.jngkzbird.arknights_angelina_pet.spine38

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * spine38 Kotlin 移植指纹验证 — 期望值来自鸿蒙版 TestPage（Python 基准，真机验证通过）。
 * 骨架指纹：setup 姿态骨和 + 包围盒 + update_cache 长度。
 * 动画指纹：30 个检查点（model|anim|t|fx|fy|bounds）。
 */
class Spine38FingerprintTest {

    class StubLoader : AttachmentLoader() {
        override fun new_region_attachment(skin: Skin, name: String, path: String): RegionAttachment? =
            RegionAttachment(name)

        override fun new_mesh_attachment(skin: Skin, name: String, path: String): MeshAttachment? =
            MeshAttachment(name)

        override fun new_bounding_box_attachment(skin: Skin, name: String): BoundingBoxAttachment? =
            BoundingBoxAttachment(name)

        override fun new_path_attachment(skin: Skin, name: String): PathAttachment? = PathAttachment(name)

        override fun new_point_attachment(skin: Skin, name: String): PointAttachment? = PointAttachment(name)

        override fun new_clipping_attachment(skin: Skin, name: String): ClippingAttachment? =
            ClippingAttachment(name)
    }

    // Python 基准（setup 姿态骨骼指纹，2026-08-14 生成）
    data class Expected(
        val model: String, val bones: Int, val fx: Double, val fy: Double, val fa: Double, val fb: Double,
        val fc: Double, val fd: Double, val fr: Double, val bx: Double, val by: Double, val bw: Double,
        val bh: Double, val cache: Int
    )

    private val expected = listOf(
        Expected("build", 172, -2543.916485, 31300.559933, 17.382774, 81.536394,
            -81.532680, 17.381254, -2179.849955, -245.47, -4.50, 397.70, 465.00, 188),
        Expected("back", 148, -3374.049788, 25214.711230, 12.889570, 74.721280,
            -74.717566, 12.888051, -2003.069962, -240.65, -4.50, 395.85, 465.00, 156),
        Expected("front", 174, -2542.116401, 31853.749926, 18.401273, 82.536223,
            -82.532509, 18.399754, -2179.849955, -240.65, -4.50, 392.88, 465.00, 192)
    )

    // 动画指纹基准（Python 2026-08-14 生成）：model|动画|t|fx|fy|bounds
    private val animExpected = listOf(
        "build|Default|0|1333.235697|37820.491486|-208.91|27.64|479.79|427.72",
        "build|Default|0|1333.235697|37820.491486|-208.91|27.64|479.79|427.72",
        "build|Interact|0|1333.235697|37820.491486|-208.91|27.64|479.79|427.72",
        "build|Interact|1.9166666269|1737.099494|37972.277604|-211.87|12.58|484.75|440.44",
        "build|Move|0|1808.322748|36726.429292|-213.93|24.23|488.11|426.39",
        "build|Move|0.5|1853.153723|33924.932187|-225.52|5.81|500.53|427.47",
        "build|Relax|0|1333.235697|37820.491486|-208.91|27.64|479.79|427.72",
        "build|Relax|2|1333.235697|37820.491486|-208.91|27.64|479.79|427.72",
        "build|Sit|0|-1503.845843|11395.702368|-197.73|-132.18|353.70|414.38",
        "build|Sit|1.3333333731|-245.795516|11446.555119|-206.89|-132.16|356.81|422.71",
        "back|Attack|0|-1959.121114|26171.992206|-268.33|-2.41|428.96|424.78",
        "back|Attack|0.75|-1162.120094|28339.651263|-246.89|-2.41|478.89|461.62",
        "back|Default|0|-1667.931112|26199.542205|-268.33|-2.41|428.96|424.78",
        "back|Default|0|-1667.931112|26199.542205|-268.33|-2.41|428.96|424.78",
        "back|Idle|0|-1667.931112|26199.542205|-268.33|-2.41|428.96|424.78",
        "back|Idle|2|-1667.931112|26199.542205|-268.33|-2.41|428.96|424.78",
        "back|Skill_1_End|0|-2449.715512|29235.737036|-243.02|34.31|490.33|419.44",
        "back|Skill_1_End|0.5|-4751.571651|30297.067216|-348.08|39.23|543.52|414.95",
        "back|Skill_1_Idle|0|-2449.715512|29235.737036|-243.02|34.31|490.33|419.44",
        "back|Skill_1_Idle|2|-2449.715512|29235.737036|-243.02|34.31|490.33|419.44",
        "front|Attack|0|-1561.713443|33156.465588|-270.64|-1.64|433.96|420.51",
        "front|Attack|0.75|135.115436|34318.272678|-260.67|-1.41|476.86|463.65",
        "front|Attack_Down|0|-1561.713443|33156.465588|-270.64|-1.64|433.96|420.51",
        "front|Attack_Down|0.75|395.291693|33580.522801|-238.71|-1.41|438.37|463.65",
        "front|Default|0|-1270.523441|33184.015587|-270.64|-1.64|433.96|420.51",
        "front|Default|0|-1270.523441|33184.015587|-270.64|-1.64|433.96|420.51",
        "front|Die|0|-1270.523441|33184.015587|-270.64|-1.64|433.96|420.51",
        "front|Die|0.5|-3954.236729|32834.632181|-331.54|-1.52|502.48|419.66",
        "front|Idle|0|-1270.523441|33184.015587|-270.64|-1.64|433.96|420.51",
        "front|Idle|2|-1270.523441|33184.015587|-270.64|-1.64|433.96|420.51"
    )

    private fun loadSkeletonData(model: String): SkeletonData {
        val path = "spine/$model/${if (model == "build") "build_char_1015_aglna2.skel" else "char_1015_aglna2.skel"}"
        val bytes = javaClass.classLoader!!.getResourceAsStream(path)!!.readBytes()
        return SkeletonBinary(StubLoader()).read_skeleton_data(bytes)
    }

    private fun boneSums(sk: Skeleton): DoubleArray {
        var fx = 0.0
        var fy = 0.0
        for (bone in sk.bones) {
            fx += bone.world_x
            fy += bone.world_y
        }
        return doubleArrayOf(fx, fy)
    }

    @Test
    fun skeletonSetupFingerprints() {
        val failures = ArrayList<String>()
        for ((i, model) in listOf("build", "back", "front").withIndex()) {
            val sd = loadSkeletonData(model)
            val sk = Skeleton(sd)
            sk.set_to_setup_pose()
            sk.update_world_transform()
            var fx = 0.0
            var fy = 0.0
            var fa = 0.0
            var fb = 0.0
            var fc = 0.0
            var fd = 0.0
            var fr = 0.0
            for (bone in sk.bones) {
                fx += bone.world_x
                fy += bone.world_y
                fa += bone.a
                fb += bone.b
                fc += bone.c
                fd += bone.d
                fr += bone.rotation
            }
            val bounds = sk.get_bounds()
            val exp = expected[i]
            val ok = exp.bones == sd.bones.size &&
                abs(fx - exp.fx) < 0.001 && abs(fy - exp.fy) < 0.001 &&
                abs(fa - exp.fa) < 0.001 && abs(fb - exp.fb) < 0.001 &&
                abs(fc - exp.fc) < 0.001 && abs(fd - exp.fd) < 0.001 &&
                abs(fr - exp.fr) < 0.001 &&
                abs(bounds[0] - exp.bx) < 0.01 && abs(bounds[1] - exp.by) < 0.01 &&
                abs(bounds[2] - exp.bw) < 0.01 && abs(bounds[3] - exp.bh) < 0.01 &&
                sk.update_cache.size == exp.cache
            if (!ok) {
                failures.add(
                    "[$model] fx=${fmt(fx)} fy=${fmt(fy)} fa=${fmt(fa)} fb=${fmt(fb)} fc=${fmt(fc)} fd=${fmt(fd)} " +
                        "fr=${fmt(fr)} bounds=(${fmt(bounds[0])},${fmt(bounds[1])},${fmt(bounds[2])},${fmt(bounds[3])}) " +
                        "cache=${sk.update_cache.size}"
                )
            }
        }
        assertTrue("骨架指纹失败:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun animationFingerprints() {
        val failures = ArrayList<String>()
        var total = 0
        var pass = 0
        for (model in listOf("build", "back", "front")) {
            val sd = loadSkeletonData(model)
            val sk = Skeleton(sd)
            for (entry in animExpected) {
                val parts = entry.split('|')
                if (parts[0] != model) {
                    continue
                }
                total++
                val anim = sd.find_animation(parts[1])
                val t = parts[2].toDouble()
                val exp_fx = parts[3].toDouble()
                val exp_fy = parts[4].toDouble()
                val exp_bx = parts[5].toDouble()
                val exp_by = parts[6].toDouble()
                val exp_bw = parts[7].toDouble()
                val exp_bh = parts[8].toDouble()
                sk.set_to_setup_pose()
                apply_animation(anim!!, sk, t, true, 1.0)
                sk.update_world_transform()
                val sums = boneSums(sk)
                val ab = sk.get_bounds()
                val ok = abs(sums[0] - exp_fx) < 0.01 && abs(sums[1] - exp_fy) < 0.01 &&
                    abs(ab[0] - exp_bx) < 0.01 && abs(ab[1] - exp_by) < 0.01 &&
                    abs(ab[2] - exp_bw) < 0.01 && abs(ab[3] - exp_bh) < 0.01
                if (ok) {
                    pass++
                } else {
                    failures.add(
                        "[$model|${parts[1]}|t=$t] 实际 fx=${fmt(sums[0])} fy=${fmt(sums[1])} " +
                            "bounds=(${fmt(ab[0])},${fmt(ab[1])},${fmt(ab[2])},${fmt(ab[3])})"
                    )
                }
            }
        }
        assertTrue("动画指纹 $pass/$total 通过，失败:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun fmt(v: Double): String = String.format("%.6f", v)

    @Test
    fun meshUvsMatchPythonReference() {
        // Python 基准（2026-08-19 生成）：F_R_Braid_B uvs 前 4 个 + 独立 UV 对总数
        val path = "spine/build/build_char_1015_aglna2.skel"
        val atlasPath = "spine/build/build_char_1015_aglna2.atlas"
        val bytes = javaClass.classLoader!!.getResourceAsStream(path)!!.readBytes()
        val atlasText = javaClass.classLoader!!.getResourceAsStream(atlasPath)!!.readBytes().toString(Charsets.UTF_8)
        val atlas = TextureAtlas(atlasText)
        val sd = SkeletonBinary(AtlasAttachmentLoaderImpl(atlas)).read_skeleton_data(bytes)
        val sk = Skeleton(sd)
        sk.set_to_setup_pose()
        sk.update_world_transform()
        var first: MeshAttachment? = null
        var totalDistinct = 0
        for (slot in sk.draw_order) {
            val att = slot.attachment as? MeshAttachment ?: continue
            val uvs = att.uvs ?: continue
            val set = HashSet<Long>()
            var i = 0
            while (i < uvs.size) {
                val uq = (uvs[i] * 10000).toLong()
                val vq = (uvs[i + 1] * 10000).toLong()
                set.add((uq shl 32) or (vq and 0xFFFFFFFFL))
                i += 2
            }
            totalDistinct += set.size
            if (first == null && slot.data.name == "F_R_Braid_B") {
                first = att
            }
        }
        val f = first!!
        println("KOTLIN ruvs[:6]=" + f.region_uvs!!.take(6).joinToString(",") { "%.4f".format(it) })
        val expected = doubleArrayOf(0.3799, 0.6729, 0.3990, 0.7062)
        val ok = abs(f.uvs!![0] - expected[0]) < 0.001 && abs(f.uvs!![1] - expected[1]) < 0.001 &&
            abs(f.uvs!![2] - expected[2]) < 0.001 && abs(f.uvs!![3] - expected[3]) < 0.001
        assertTrue(
            "uvs=${f.uvs!!.take(4)} 期望=${expected.joinToString(",")} 独立UV对数=$totalDistinct（Python=1029）",
            ok && totalDistinct > 1000
        )
    }
}
