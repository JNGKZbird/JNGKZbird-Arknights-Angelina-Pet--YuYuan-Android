package com.jngkzbird.arknights_angelina_pet.model

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

data class AssetFile(
    val path: String,
    val size: Long,
    val sha256: String
)

data class AssetManifest(
    val pet: String,
    val petName: String,
    val platform: String,
    val version: String,
    val releaseTag: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val uncompressedSize: Long,
    val fileCount: Int,
    val extractRoot: String,
    val files: List<AssetFile>
)

class AssetHashMismatch(message: String) : IOException(message)

class AssetCancelled(message: String) : IOException(message)

/**
 * 角色素材（Spine/语音）不随 APK 分发：manifest 打进 assets，
 * 素材包下载/手动导入后落到 filesDir/plugins/<pet>/<version>/。
 */
class AssetStore(
    private val context: Context,
    private val urlOverride: String? = null
) {
    val manifest: AssetManifest = loadManifest(context)
    val pluginDir: File = File(context.filesDir, "plugins/${manifest.pet}/${manifest.version}")
    val downloadUrl: String = urlOverride?.takeIf { it.isNotBlank() } ?: manifest.url

    fun file(relative: String): File = File(pluginDir, relative)

    fun isInstalled(): Boolean = manifest.files.all { item ->
        val f = file(item.path)
        f.isFile && f.length() == item.size
    }

    fun installFromZip(
        zipFile: File,
        checkHash: Boolean = true,
        onProgress: (Int) -> Unit = {}
    ) {
        verifyZip(zipFile, checkHash, onProgress)
        val temp = File(context.cacheDir, "asset_extract_${System.currentTimeMillis()}")
        if (temp.exists()) temp.deleteRecursively()
        temp.mkdirs()
        try {
            ZipFile(zipFile).use { zf ->
                val entries = zf.entries()
                var done = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = normalize(entry.name)
                    val target = File(temp, name)
                    target.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    done++
                    if (manifest.fileCount > 0) {
                        onProgress(done * 100 / manifest.fileCount)
                    }
                }
            }
            pluginDir.mkdirs()
            for (item in manifest.files) {
                val source = File(temp, item.path)
                val target = file(item.path)
                target.parentFile?.mkdirs()
                if (target.exists()) target.delete()
                source.copyTo(target, overwrite = true)
            }
            File(pluginDir, ".installed.json").writeText(
                JSONObject().apply {
                    put("asset_version", manifest.version)
                    put("sha256", manifest.sha256)
                    put("installed_at", System.currentTimeMillis())
                }.toString()
            )
        } finally {
            temp.deleteRecursively()
        }
    }

    fun download(
        dest: File,
        onProgress: (Long, Long) -> Unit,
        cancelled: () -> Boolean
    ) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "AngelinaPet-AssetInstaller/1.0")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("下载响应为空")
                val total = if (body.contentLength() > 0) body.contentLength() else manifest.size
                body.byteStream().use { input ->
                    FileOutputStream(dest).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var done = 0L
                        while (true) {
                            if (cancelled()) throw AssetCancelled("已取消下载")
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }

    fun verifyZip(
        zipFile: File,
        checkHash: Boolean = true,
        onProgress: (Int) -> Unit = {}
    ) {
        if (!zipFile.isFile) throw IOException("找不到 zip 文件")
        if (checkHash) {
            val actual = sha256(zipFile)
            if (!actual.equals(manifest.sha256, ignoreCase = true)) {
                throw AssetHashMismatch("zip 的 SHA-256 与素材清单不一致")
            }
        }
        val expected = manifest.files.associateBy { it.path }
        val seen = HashSet<String>()
        var total = 0L
        ZipFile(zipFile).use { zf ->
            val entries = zf.entries()
            var count = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                count++
                val name = normalize(entry.name)
                if (name.startsWith("/") || name.split("/").contains("..")) {
                    throw IOException("zip 内含不安全路径：$name")
                }
                if (!seen.add(name)) throw IOException("zip 内含重名文件：$name")
                val item = expected[name] ?: throw IOException("zip 内含未知文件：$name")
                if (entry.size != item.size) throw IOException("文件大小不符：$name")
                val digest = MessageDigest.getInstance("SHA-256")
                zf.getInputStream(entry).use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                if (!digest.digest().toHex().equals(item.sha256, ignoreCase = true)) {
                    throw IOException("文件内容校验失败：$name")
                }
                total += entry.size
                if (manifest.fileCount > 0) {
                    onProgress(count * 100 / manifest.fileCount)
                }
            }
            if (count != manifest.fileCount) {
                throw IOException("zip 文件数量不符：$count，应为 ${manifest.fileCount}")
            }
            if (total > manifest.uncompressedSize + 1048576) {
                throw IOException("zip 解压后体积异常")
            }
        }
    }

    private fun normalize(name: String): String = name.replace('\\', '/')

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    companion object {
        fun loadManifest(context: Context): AssetManifest {
            val text = context.assets.open("asset_manifest.json")
                .bufferedReader()
                .use { it.readText() }
            val o = JSONObject(text)
            val filesJson = o.getJSONArray("files")
            val files = ArrayList<AssetFile>(filesJson.length())
            for (i in 0 until filesJson.length()) {
                val item = filesJson.getJSONObject(i)
                files.add(
                    AssetFile(
                        path = item.getString("path"),
                        size = item.getLong("size"),
                        sha256 = item.getString("sha256")
                    )
                )
            }
            return AssetManifest(
                pet = o.optString("pet", "angelina"),
                petName = o.optString("pet_name", "予愿安洁莉娜"),
                platform = o.optString("platform", "android"),
                version = o.getString("asset_version"),
                releaseTag = o.optString("release_tag", ""),
                url = o.getString("url"),
                sha256 = o.getString("sha256"),
                size = o.getLong("size"),
                uncompressedSize = o.getLong("uncompressed_size"),
                fileCount = o.optInt("file_count", files.size),
                extractRoot = o.optString("extract_root", "plugin"),
                files = files
            )
        }
    }
}

private fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xff
        out.append("0123456789abcdef"[v ushr 4])
        out.append("0123456789abcdef"[v and 0x0f])
    }
    return out.toString()
}
