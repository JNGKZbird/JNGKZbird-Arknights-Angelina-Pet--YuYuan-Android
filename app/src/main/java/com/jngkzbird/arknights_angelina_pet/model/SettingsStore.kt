package com.jngkzbird.arknights_angelina_pet.model

import android.content.Context
import org.json.JSONObject
import java.io.File

/** 设置持久化（filesDir/settings.json；主题存 SharedPreferences） */
object SettingsStore {
    fun loadSettings(context: Context): ChatSettings {
        val f = File(context.filesDir, "settings.json")
        val s = ChatSettings()
        try {
            if (!f.exists()) {
                return s
            }
            val o = JSONObject(f.readText())
            s.chat_base_url = o.optString("chat_base_url", s.chat_base_url)
            s.chat_api_key = o.optString("chat_api_key", s.chat_api_key)
            s.chat_model = o.optString("chat_model", s.chat_model)
            s.player_name = o.optString("player_name", s.player_name)
            s.context_window_size = o.optInt("context_window_size", s.context_window_size)
            s.time_awareness = o.optBoolean("time_awareness", s.time_awareness)
            s.extra_prompt = o.optString("extra_prompt", s.extra_prompt)
            s.chatter_interval = o.optString("chatter_interval", s.chatter_interval)
            s.voice_enabled = o.optBoolean("voice_enabled", s.voice_enabled)
            s.voice_language = o.optString("voice_language", s.voice_language)
        } catch (_: Exception) {
        }
        return s
    }

    fun saveSettings(context: Context, s: ChatSettings) {
        val f = File(context.filesDir, "settings.json")
        val o = JSONObject()
        o.put("chat_base_url", s.chat_base_url)
        o.put("chat_api_key", s.chat_api_key)
        o.put("chat_model", s.chat_model)
        o.put("player_name", s.player_name)
        o.put("context_window_size", s.context_window_size)
        o.put("time_awareness", s.time_awareness)
        o.put("extra_prompt", s.extra_prompt)
        o.put("chatter_interval", s.chatter_interval)
        o.put("voice_enabled", s.voice_enabled)
        o.put("voice_language", s.voice_language)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(f)
    }

    fun loadTheme(context: Context): String =
        context.getSharedPreferences("pet_settings", Context.MODE_PRIVATE)
            .getString("pet_theme", "sky") ?: "sky"

    fun saveTheme(context: Context, name: String) {
        context.getSharedPreferences("pet_settings", Context.MODE_PRIVATE)
            .edit().putString("pet_theme", name).apply()
    }

    // ── 项目偏好 ──

    fun loadProjectCurrent(context: Context): String =
        context.getSharedPreferences("pet_settings", Context.MODE_PRIVATE)
            .getString("project_current", "") ?: ""

    fun saveProjectCurrent(context: Context, pid: String) {
        context.getSharedPreferences("pet_settings", Context.MODE_PRIVATE)
            .edit().putString("project_current", pid).apply()
    }

    fun loadExpandedPids(context: Context): String =
        context.getSharedPreferences("pet_settings", Context.MODE_PRIVATE)
            .getString("project_expanded", "") ?: ""

    fun saveExpandedPids(context: Context, pids: String) {
        context.getSharedPreferences("pet_settings", Context.MODE_PRIVATE)
            .edit().putString("project_expanded", pids).apply()
    }

    fun loadAgentCurrent(context: Context): String =
        context.getSharedPreferences("pet_settings", Context.MODE_PRIVATE)
            .getString("agent_current", "") ?: ""

    fun saveAgentCurrent(context: Context, aid: String) {
        context.getSharedPreferences("pet_settings", Context.MODE_PRIVATE)
            .edit().putString("agent_current", aid).apply()
    }
}
