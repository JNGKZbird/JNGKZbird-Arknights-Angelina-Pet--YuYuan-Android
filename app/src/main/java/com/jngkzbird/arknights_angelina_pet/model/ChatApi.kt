package com.jngkzbird.arknights_angelina_pet.model

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LLM 流式 API — 鸿蒙版 ChatApi 移植（OkHttp SSE）。
 * 铁律：必须流式接口（普通请求聚合缓冲）；SSE 跨块截断的多字节 UTF-8 字符由 lineBuf 字节暂存保护。
 */
class ChatApi {
    interface Callbacks {
        fun onDelta(delta: String)
        fun onReasoning(delta: String)
        fun onDone(full: String)
        fun onError(err: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    var running = false
        private set
    private var stopped = false

    companion object {
        fun buildUrl(baseUrl: String): String {
            var url = baseUrl.trim()
            if (url.endsWith("/")) {
                url = url.dropLast(1)
            }
            return url + "/chat/completions"
        }

        // 解析一段 SSE 文本（可能含多个事件），返回增量列表（type: content/reasoning）
        fun parseSseChunk(chunk: String): List<Pair<String, String>> {
            val deltas = ArrayList<Pair<String, String>>()
            for (rawLine in chunk.split('\n')) {
                val t = rawLine.trim()
                if (!t.startsWith("data:")) {
                    continue
                }
                val payload = t.substring(5).trim()
                if (payload == "[DONE]" || payload.isEmpty()) {
                    continue
                }
                try {
                    val obj = JSONObject(payload)
                    val choices = obj.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) {
                        continue
                    }
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    // 坑（org.json 怪癖）：optString 遇 JSON null 返回字面量 "null" 而非回退值
                    // → 思考阶段的 content:null / 内容阶段的 reasoning_content:null 会混进正文
                    val content = if (delta.isNull("content")) "" else delta.optString("content", "")
                    if (content.isNotEmpty()) {
                        deltas.add("content" to content)
                    }
                    // 思考模型：推理增量走 reasoning_content（DeepSeek 系 thinking 模式）
                    val reasoning = if (delta.isNull("reasoning_content")) "" else delta.optString("reasoning_content", "")
                    if (reasoning.isNotEmpty()) {
                        deltas.add("reasoning" to reasoning)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ChatApi", "非 JSON SSE 行: ${payload.take(120)}")
                }
            }
            return deltas
        }
    }

    fun stop() {
        stopped = true
    }

    fun send(baseUrl: String, apiKey: String, model: String, messages: List<ChatMsg>, cb: Callbacks) {
        if (running) {
            return
        }
        running = true
        stopped = false
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                for (m in messages) {
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            })
            put("temperature", 0.8)
            put("stream", true)
        }
        val request = Request.Builder()
            .url(buildUrl(baseUrl))
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()
        Thread {
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        cb.onError("HTTP ${resp.code}: ${resp.body?.string()?.take(200) ?: ""}")
                        return@Thread
                    }
                    val source = resp.body!!.source()
                    var full = StringBuilder()
                    var lineBuf = ByteArray(0)
                    val buf = okio.Buffer()
                    while (true) {
                        if (stopped) {
                            cb.onDone(full.toString())
                            return@Thread
                        }
                        val n = source.read(buf, 8192L)
                        if (n == -1L) {
                            break
                        }
                        if (buf.size == 0L) {
                            continue
                        }
                        val chunkBytes = buf.readByteArray()
                        // lineBuf 字节暂存：跨块截断的 UTF-8 字符保持完整
                        val all = lineBuf + chunkBytes
                        // 找最后一个换行：其后为不完整行
                        var lastNl = -1
                        for (i in all.indices) {
                            if (all[i] == '\n'.code.toByte()) {
                                lastNl = i
                            }
                        }
                        val completeBytes = if (lastNl >= 0) all.copyOfRange(0, lastNl + 1) else ByteArray(0)
                        lineBuf = if (lastNl >= 0) all.copyOfRange(lastNl + 1, all.size) else all
                        if (completeBytes.isEmpty()) {
                            continue
                        }
                        val chunkText = String(completeBytes, Charsets.UTF_8)
                        for ((type, delta) in parseSseChunk(chunkText)) {
                            if (type == "reasoning") {
                                cb.onReasoning(delta)
                            } else {
                                full.append(delta)
                                cb.onDelta(delta)
                            }
                        }
                    }
                    cb.onDone(full.toString())
                }
            } catch (e: Exception) {
                cb.onError(e.message ?: "网络错误")
            } finally {
                running = false
            }
        }.start()
    }

    /** 测试连接：非流式最小请求（max_tokens 8） */
    fun test(baseUrl: String, apiKey: String, model: String, cb: Callbacks) {
        val wrapper = object : Callbacks {
            override fun onDelta(delta: String) {
            }

            override fun onReasoning(delta: String) {
            }

            override fun onDone(full: String) {
                cb.onDone(full)
            }

            override fun onError(err: String) {
                cb.onError(err)
            }
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "请回复：酸橙")))
            put("max_tokens", 8)
            put("stream", false)
        }
        val request = Request.Builder()
            .url(buildUrl(baseUrl))
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()
        Thread {
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        wrapper.onError("HTTP ${resp.code}")
                        return@Thread
                    }
                    val text = resp.body!!.string()
                    try {
                        val obj = JSONObject(text)
                        val message = obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        val content = if (message.isNull("content")) "" else message.optString("content", "")
                        if (content.isNotEmpty()) {
                            wrapper.onDone("连接成功：$content")
                            return@Thread
                        }
                    } catch (_: Exception) {
                    }
                    wrapper.onDone("连接成功")
                }
            } catch (e: Exception) {
                wrapper.onError("连接失败：${e.message?.take(100)}")
            }
        }.start()
    }
}
