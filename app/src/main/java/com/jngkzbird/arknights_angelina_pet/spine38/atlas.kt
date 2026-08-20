package com.jngkzbird.arknights_angelina_pet.spine38

/**
 * Spine 3.8 图集解析 — ported from spine38/atlas.ets（spine-ts 3.8 TextureAtlas）。
 * AtlasRegion 与附件类定义在 loader.kt；本模块单向依赖 loader。
 */
class AtlasPage(val name: String) {
    var width: Int = 0
    var height: Int = 0
    var texture: Any? = null // 渲染器填充
}

class TextureAtlas(atlasText: String, textureLoader: ((pagePath: String) -> Any?)? = null) {
    val pages: MutableList<AtlasPage> = mutableListOf()
    val regions: MutableList<AtlasRegion> = mutableListOf()

    init {
        val lines = atlasText.split('\n')
        var page: AtlasPage? = null
        var region: AtlasRegion? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) {
                continue
            }
            // 键值对：冒号前的部分是已知属性键
            val colon = line.indexOf(':')
            if (colon != -1) {
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                if (key == "size" && page != null && region == null) {
                    val parts = value.split(',')
                    page.width = parts[0].trim().toInt()
                    page.height = parts[1].trim().toInt()
                } else if (key == "rotate" && region != null) {
                    region.rotate = value == "true"
                } else if (key == "xy" && region != null) {
                    val parts = value.split(',')
                    region.x = parts[0].trim().toInt()
                    region.y = parts[1].trim().toInt()
                } else if (key == "size" && region != null) {
                    val parts = value.split(',')
                    region.width = parts[0].trim().toInt()
                    region.height = parts[1].trim().toInt()
                } else if (key == "orig" && region != null) {
                    val parts = value.split(',')
                    region.original_width = parts[0].trim().toInt()
                    region.original_height = parts[1].trim().toInt()
                } else if (key == "offset" && region != null) {
                    val parts = value.split(',')
                    region.offset_x = parts[0].trim().toInt()
                    region.offset_y = parts[1].trim().toInt()
                }
                // 其余键（format/filter/repeat/index/split/pad/pma）忽略
                continue
            }
            // 非键值行：页面名或区域名
            if (page == null) {
                page = AtlasPage(line)
                pages.add(page)
                textureLoader?.let { page.texture = it(line) }
            } else {
                region = AtlasRegion()
                region.name = line
                region.page = page
                regions.add(region)
            }
        }

        // 计算 UV（照 spine-ts 3.8 TextureAtlas：旋转区域 u2 用 height、v2 用 width；
        // width/height 保持 atlas 原文语义不交换）
        for (region in regions) {
            val page = region.page as AtlasPage
            val pw = if (page.width != 0) page.width else 1
            val ph = if (page.height != 0) page.height else 1
            region.page_width = page.width
            region.page_height = page.height
            if (region.original_width == 0) {
                region.original_width = region.width
            }
            if (region.original_height == 0) {
                region.original_height = region.height
            }
            region.u = region.x / pw.toDouble()
            region.v = region.y / ph.toDouble()
            if (region.rotate) {
                region.u2 = (region.x + region.height) / pw.toDouble()
                region.v2 = (region.y + region.width) / ph.toDouble()
                region.degrees = 90
            } else {
                region.u2 = (region.x + region.width) / pw.toDouble()
                region.v2 = (region.y + region.height) / ph.toDouble()
            }
        }
    }

    fun find_region(name: String): AtlasRegion? = regions.firstOrNull { it.name == name }
}

// 按图集解析附件，填充 region 引用
class AtlasAttachmentLoaderImpl(private val atlas: TextureAtlas) : AttachmentLoader() {
    override fun new_region_attachment(skin: Skin, name: String, path: String): RegionAttachment? {
        val region = atlas.find_region(path)
            ?: throw IllegalStateException("Region not found in atlas: $path (region attachment: $name)")
        val a = RegionAttachment(name)
        a.region = region
        return a
    }

    override fun new_mesh_attachment(skin: Skin, name: String, path: String): MeshAttachment? {
        val region = atlas.find_region(path)
            ?: throw IllegalStateException("Region not found in atlas: $path (mesh attachment: $name)")
        val a = MeshAttachment(name)
        a.region = region
        return a
    }

    override fun new_bounding_box_attachment(skin: Skin, name: String): BoundingBoxAttachment? =
        BoundingBoxAttachment(name)

    override fun new_path_attachment(skin: Skin, name: String): PathAttachment? = PathAttachment(name)

    override fun new_point_attachment(skin: Skin, name: String): PointAttachment? = PointAttachment(name)

    override fun new_clipping_attachment(skin: Skin, name: String): ClippingAttachment? = ClippingAttachment(name)
}
