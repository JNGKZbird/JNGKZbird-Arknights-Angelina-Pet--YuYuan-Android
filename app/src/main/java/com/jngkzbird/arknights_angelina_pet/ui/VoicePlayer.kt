package com.jngkzbird.arknights_angelina_pet.ui

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.util.Calendar

/**
 * 安洁莉娜语音 — 鸿蒙版 Voice 模型 + PetPage 语音逻辑移植。
 * SoundPool 播放（低延迟音效）；触发：点击（10s 冷却内=戳一下）/闲置 60s/启动问候/模式切换。
 */
class VoicePlayer(private val context: Context) {
    companion object {
        private const val TAG = "PetGL"

        // 语音名 → assets/voice/ 文件名
        val VOICE_FILES: Map<String, String> = mapOf(
            "交谈1" to "talk1.wav", "交谈2" to "talk2.wav", "交谈3" to "talk3.wav",
            "晋升后交谈1" to "promo1.wav", "晋升后交谈2" to "promo2.wav",
            "信赖提升后交谈1" to "trust1.wav", "信赖提升后交谈2" to "trust2.wav",
            "信赖提升后交谈3" to "trust3.wav",
            "戳一下" to "poke.wav",
            "选中干员1" to "select1.wav", "选中干员2" to "select2.wav",
            "作战中1" to "fight1.wav", "作战中2" to "fight2.wav",
            "作战中3" to "fight3.wav", "作战中4" to "fight4.wav",
            "部署1" to "deploy1.wav", "部署2" to "deploy2.wav",
            "问候" to "greeting.wav", "闲置" to "idle.wav",
            "干员报到" to "report.wav", "任命助理" to "assistant.wav",
            "生日" to "birthday.wav", "新年祝福" to "newyear.wav",
            "周年庆典" to "anniv.wav"
        )

        // 台词字幕（鸿蒙版 VOICE_LINES 原文）
        val VOICE_LINES: Map<String, String> = mapOf(
            "任命助理" to "博士，今天没有急件要送，我来帮你整理一下文件吧。放心放心，这么久了，这些文件的分类我早就一清二楚了。",
            "交谈1" to "博士，这些是我从雷姆必拓带回来的伴手礼——胡萝卜面霜能改善皮肤，占卜书会建议你每天干什么、不干什么......哼哼，我是不是很有眼光？",
            "交谈2" to "信使不能只盯着脚下的路，如果因为太忧心包裹里的秘密，而错过了沿途的风景，那也是了不得的遗憾......没错，我的确在想，等到哪天闲下来，我说不定能写一本游记呢。",
            "交谈3" to "这张照片？上次我和塔妮她们在天台吃午饭，发现了一辆巡逻小车，四个人就一起对着它摆了个pose......博士也想拍一张吗？好呀，我现在就带你去！",
            "晋升后交谈1" to "我已经不是刚来时那个高中生啦，这些年除了工作，学业、朋友我也没落下。真要说有什么遗憾的话......如果可以，我想参加一次自己的毕业典礼呢。",
            "晋升后交谈2" to "我偶尔会不自觉地想起那片雷姆必拓的废墟，想象舰船如何变得支离破碎......不，不要紧的。现在的我只想飞得快一点，再快一点，快到无论发生什么事，我都能及时赶到你的身边。",
            "信赖提升后交谈1" to "如果能回叙拉古生活，我会回去吗？虽然我很想念故乡的父母和朋友，但就像罗德岛的大家习惯了有我的生活，叙拉古的他们也习惯了没有我的生活。为了更好地相见，先用书信传递彼此的思念吧。",
            "信赖提升后交谈2" to "当信使久了，我也养成了写信的习惯。感染者的一生实在短暂，如果终有离去的那天，我希望我写的一封封信，也能以另一种形式陪伴......我不愿忘记的人，和不愿忘记我的人。",
            "信赖提升后交谈3" to "看，每送出一封信，我就会折一颗纸星星。对，纸星星无法升上天空，但它们是安洁莉娜的星星，它们仍然可以离开狭小的玻璃瓶，飞翔、环绕、起舞......在这片星空中，和我跳一支舞吧。",
            "闲置" to "咖啡的味道和以前一样，我们之间也和以前一样......嗯，这样就好。",
            "干员报到" to "安心院安洁莉娜，回来向您报到......嘿嘿，突然这么正式是不是有点不习惯，博士？",
            "选中干员1" to "你的信，我肯定加急派送。",
            "选中干员2" to "这封信要送到哪里？",
            "作战中1" to "我也不是只会让东西变轻哦，老实待在那里吧。",
            "作战中2" to "跑得不够快的敌人，是会被风吹落的。",
            "作战中3" to "没写清收件地址的信件，是会被退回的！",
            "作战中4" to "让开让开，还有人等着这封信呢！",
            "戳一下" to "嗯哼？",
            "新年祝福" to "还在加班吗？烟花表演已经结束了......真是没办法，你看好哦。小水滴慢慢飘起来，越来越高——嘭！这是我送你的小烟花，以及，新年快乐，博士。",
            "问候" to "早安，博士！我今天有外出任务，所以跟你提前说午安和晚安啦！",
            "生日" to "博士，sorridi~生日快乐，以后每年你生日，我们都要拍张照片留念，然后集满整本相簿......到时候你想要相簿当礼物？不行不行，还是由我来保管吧。",
            "周年庆典" to "你不知道我为了赶上庆典，究竟是怎么跋山涉水的。唔，我是该先对你说周年寄语，还是和你讲讲这一路的见闻......噗，我真的有好多话想跟你说，反正时间还有很多，我们慢慢聊吧。",
            "部署1" to "你的信，我肯定加急派送。",
            "部署2" to "这封信要送到哪里？"
        )

        val BASE_TALK = listOf("交谈1", "交谈2", "交谈3", "晋升后交谈1", "晋升后交谈2", "信赖提升后交谈1", "信赖提升后交谈2", "信赖提升后交谈3")
        val COMBAT_SELECT = listOf("选中干员1", "选中干员2")
        val COMBAT_FIGHT = listOf("作战中1", "作战中2", "作战中3", "作战中4")
        val COMBAT_DEPLOY = listOf("部署1", "部署2")
    }

    private var loaded = false
    private var voiceEnabled = true
    private var language = "中文"
    private var currentPlayer: MediaPlayer? = null
    var voiceCooldown = false
        private set
    var onSubtitle: ((String, Long) -> Unit)? = null // 字幕回调（气泡显示）

    fun init() {
        if (loaded) {
            return
        }
        loaded = true
        reload()
    }

    /** 语言切换：中文 voice/ 日文 voice_jp/（重载音效） */
    fun setLanguage(lang: String) {
        if (lang == language) {
            return
        }
        language = lang
        reload()
    }

    private fun voiceDir(): String = if (language == "日文") "voice_jp/" else "voice/"

    private fun reload() {
        // MediaPlayer 每次播放即时加载（wav 小文件），reload 仅做目录存在性验证
        try {
            context.assets.openFd(voiceDir() + "talk1.wav").use { }
        } catch (e: Exception) {
            Log.w("PetGL", "voice dir check fail: ${voiceDir()} ${e.message}")
        }
    }

    fun release() {
        try {
            currentPlayer?.release()
        } catch (_: Exception) {
        }
        currentPlayer = null
    }

    fun play(name: String, subtitleMs: Long = 8000L) {
        if (!voiceEnabled) {
            Log.w("PetGL", "voice play skipped: disabled ($name)")
            return
        }
        try {
            currentPlayer?.release()
            currentPlayer = null
            val fd = context.assets.openFd(voiceDir() + (VOICE_FILES[name] ?: return))
            val mp = MediaPlayer()
            mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            mp.setOnCompletionListener { p ->
                p.release()
                if (currentPlayer === p) {
                    currentPlayer = null
                }
            }
            mp.prepare()
            mp.start()
            currentPlayer = mp
            fd.close()
        } catch (e: Exception) {
            Log.w("PetGL", "voice play FAILED: $name ${e.message}")
        }
        VOICE_LINES[name]?.let { onSubtitle?.invoke(it, subtitleMs) }
    }

    /** 点击语音（10s 冷却内=戳一下；战斗=技能开启时作战台词/否则选中台词；否则随机 BASE_TALK） */
    fun voiceClick(combat: Boolean, skillActive: Boolean) {
        if (voiceCooldown) {
            play("戳一下", 4000L)
            return
        }
        val pool = when {
            combat && skillActive -> COMBAT_FIGHT
            combat -> COMBAT_SELECT
            else -> BASE_TALK
        }
        play(pool.random())
        voiceCooldown = true
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            voiceCooldown = false
        }, 10000L)
    }

    /** 启动问候（周年庆典 05-01~04 > 新年祝福 > 问候） */
    fun greetingVoiceName(): String {
        val c = Calendar.getInstance()
        val mm = c.get(Calendar.MONTH) + 1
        val dd = c.get(Calendar.DAY_OF_MONTH)
        if (mm == 5 && dd in 1..4) {
            return "周年庆典"
        }
        if ((mm == 1 && dd in 1..3) || (mm == 1 && dd in 28..31) || (mm == 2 && (dd in 1..5 || dd in 15..22))) {
            return "新年祝福"
        }
        return "问候"
    }

    fun setEnabled(enabled: Boolean) {
        voiceEnabled = enabled
    }

    fun isLoaded(): Boolean = loaded
}
