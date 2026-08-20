package com.jngkzbird.arknights_angelina_pet.engine

/**
 * 桌宠状态机 — 直译 Windows v2.0 pet_window.py + core.py（STATE_MAP/STATE_CHAIN 语义）。
 * 纯 Kotlin 无 Android 依赖（JVM 可测）。时间驱动：tick(dt) 推进，一次性动画结束自动转移。
 */

// ── 状态常量（core.py STATE_MAP 的键） ──
const val ST_IDLE = "idle"
const val ST_INTERACT = "interact"
const val ST_MOVE = "move"
const val ST_SIT = "sit"
const val ST_SLEEP = "sleep"
const val ST_COMBAT_IDLE = "combat_idle"
const val ST_COMBAT_START = "combat_start"
const val ST_COMBAT_START2 = "combat_start2"
const val ST_ATTACK = "attack"
const val ST_ATTACK_DOWN = "attack_down"
const val ST_SKILL1_IDLE = "skill1_idle"
const val ST_SKILL1_LOOP = "skill1_loop"
const val ST_SKILL1_END = "skill1_end"
const val ST_SKILL2_BEGIN = "skill2_begin"
const val ST_SKILL2_TAKEOFF_BEGIN = "skill2_takeoff_begin"
const val ST_SKILL2_TAKEOFF_LOOP = "skill2_takeoff_loop"
const val ST_SKILL2_TAKEOFF_END = "skill2_takeoff_end"
const val ST_SKILL2_LOOP = "skill2_loop"
const val ST_SKILL2_IDLE = "skill2_idle"
const val ST_SKILL2_END = "skill2_end"
const val ST_SKILL_DOWN_1 = "skill_down_1"
const val ST_SKILL_DOWN_2 = "skill_down_2"
const val ST_FLY_BEGIN = "fly_begin"
const val ST_FLY_LOOP = "fly_loop"
const val ST_FLY_IDLE = "fly_idle"
const val ST_FLY_COMBAT = "fly_combat"
const val ST_FLY_RESTART = "fly_restart"
const val ST_FLY_END = "fly_end"
const val ST_FLY = "fly"

/** 状态 → (模型, 动画)（pet_engine.py STATE_MAP） */
val STATE_MAP: Map<String, Pair<String, String>> = mapOf(
    ST_IDLE to ("build" to "Relax"),
    ST_INTERACT to ("build" to "Interact"),
    ST_MOVE to ("build" to "Move"),
    ST_SIT to ("build" to "Sit"),
    ST_SLEEP to ("build" to "Sleep"),
    ST_COMBAT_IDLE to ("front" to "Idle"),
    ST_COMBAT_START to ("front" to "Start"),
    ST_COMBAT_START2 to ("front" to "Start_2"),
    ST_ATTACK to ("front" to "Attack"),
    ST_ATTACK_DOWN to ("front" to "Attack_Down"),
    ST_SKILL1_IDLE to ("front" to "Skill_1_Idle"),
    ST_SKILL1_LOOP to ("front" to "Skill_1_Loop"),
    ST_SKILL1_END to ("front" to "Skill_1_End"),
    ST_SKILL2_BEGIN to ("front" to "Skill_2_Begin"),
    ST_SKILL2_TAKEOFF_BEGIN to ("front" to "Skill_2_Takeoff_Begin"),
    ST_SKILL2_TAKEOFF_LOOP to ("front" to "Skill_2_Takeoff_Loop"),
    ST_SKILL2_TAKEOFF_END to ("front" to "Skill_2_Takeoff_End"),
    ST_SKILL2_LOOP to ("front" to "Skill_2_Loop"),
    ST_SKILL2_IDLE to ("front" to "Skill_2_Idle"),
    ST_SKILL2_END to ("front" to "Skill_2_End"),
    ST_SKILL_DOWN_1 to ("front" to "Skill_Down_1_Loop"),
    ST_SKILL_DOWN_2 to ("front" to "Skill_Down_2_Loop"),
    ST_FLY_BEGIN to ("front" to "Skill_3_Begin"),
    ST_FLY_LOOP to ("front" to "Skill_3_Loop"),
    ST_FLY_IDLE to ("front" to "Skill_3_Idle"),
    ST_FLY_COMBAT to ("front" to "Skill_3_Combat"),
    ST_FLY_RESTART to ("front" to "Skill_3_Restart_Begin"),
    ST_FLY_END to ("front" to "Skill_3_End"),
    ST_FLY to ("front" to "Skill_3_Move")
)

/** 一次性动画结束 → 下一状态（core.py STATE_CHAIN） */
val STATE_CHAIN: Map<String, String> = mapOf(
    ST_ATTACK to ST_ATTACK_DOWN,
    ST_ATTACK_DOWN to ST_COMBAT_IDLE,
    ST_COMBAT_START to ST_COMBAT_IDLE,
    ST_COMBAT_START2 to ST_COMBAT_IDLE,
    ST_SKILL1_LOOP to ST_SKILL1_IDLE,
    ST_SKILL2_BEGIN to ST_SKILL2_TAKEOFF_BEGIN,
    ST_SKILL2_TAKEOFF_BEGIN to ST_SKILL2_TAKEOFF_LOOP,
    ST_SKILL2_TAKEOFF_LOOP to ST_SKILL2_TAKEOFF_END,
    ST_SKILL2_TAKEOFF_END to ST_SKILL2_IDLE,
    ST_SKILL2_LOOP to ST_SKILL2_IDLE,
    ST_FLY_RESTART to ST_FLY_IDLE,
    ST_FLY_LOOP to ST_FLY_IDLE,
    ST_FLY_COMBAT to ST_FLY_IDLE,
    ST_SKILL_DOWN_1 to ST_COMBAT_IDLE,
    ST_SKILL_DOWN_2 to ST_COMBAT_IDLE
)

/** 无限循环状态（core.py LOOP_STATES） */
val LOOP_STATES: Set<String> = setOf(
    ST_IDLE, ST_COMBAT_IDLE, ST_SKILL1_IDLE, ST_SKILL2_IDLE, ST_FLY_IDLE, ST_MOVE, ST_FLY
)

val FLIGHT_STATES: Set<String> = setOf(
    ST_FLY_BEGIN, ST_FLY, ST_FLY_END, ST_FLY_IDLE, ST_FLY_LOOP, ST_FLY_COMBAT, ST_FLY_RESTART
)

/** 背面模型缺失的动画（切换背面视角时回退正面模型；pet_engine.py BACK_MISSING_ANIMS） */
val BACK_MISSING_ANIMS: Set<String> = setOf(ST_ATTACK_DOWN, ST_SKILL_DOWN_1, ST_SKILL_DOWN_2)

class PetEngine {
    var state: String = ST_IDLE
        private set
    var time: Double = 0.0 // 当前动画时间（秒）
        private set
    var hold: Boolean = false // hold 状态（sit/sleep 菜单置 true）
        private set
    var activeSkill: String? = null // "skill1"/"skill2"/"skill3"
        private set
    var combatMode: Boolean = false
        private set
    var combatView: String = "front" // front / back
        private set

    // 状态变化回调（模型切换/语音触发等）
    var onStateChanged: ((old: String, new: String) -> Unit)? = null
    var onAttack: (() -> Unit)? = null // 平A/技能攻击触发（语音）

    /** 当前状态 → (模型, 动画)；战斗背面视角切 back 模型，缺失动画的状态回退 front（照 pet_engine.py _resolve_model） */
    @Synchronized
    fun modelFor(): String {
        val (model, _) = STATE_MAP[state] ?: return "build"
        if (model == "front" && combatView == "back" && state !in BACK_MISSING_ANIMS) {
            return "back"
        }
        return model
    }

    @Synchronized
    fun animFor(): String = STATE_MAP[state]?.second ?: "Relax"

    fun animDuration(sd: com.jngkzbird.arknights_angelina_pet.spine38.SkeletonData): Double =
        sd.find_animation(animFor())?.duration ?: 0.0

    @Synchronized
    fun setState(name: String, hold: Boolean = false) {
        if (name == state && hold == this.hold) {
            return
        }
        val old = state
        state = name
        time = 0.0
        this.hold = hold
        onStateChanged?.invoke(old, name)
    }

    /**
     * 推进动画时间；一次性动画结束时自动转移。返回 true 表示状态已转移（调用方需重取模型/动画）。
     */
    @Synchronized
    fun tick(dt: Double, sd: com.jngkzbird.arknights_angelina_pet.spine38.SkeletonData): Boolean {
        if (dt <= 0.0) {
            return false
        }
        val duration = animDuration(sd)
        // Default (0 时长) 或未知动画：恒 0 时间
        if (duration <= 0.0) {
            return false
        }
        time += dt
        if (time < duration) {
            return false
        }
        // 一次性动画到末尾
        val next = resolveNext()
        if (next != null) {
            setState(next)
            return true
        }
        // 无转移（hold 冻结在末帧 / loop 状态）
        if (state in LOOP_STATES || hold) {
            time %= duration
        } else {
            time = duration // 冻结末帧
        }
        return false
    }

    /** 一次性动画结束的转移目标（照 pet_window.py 动画结束块） */
    private fun resolveNext(): String? {
        if (state in LOOP_STATES) {
            return null
        }
        when (state) {
            ST_ATTACK -> {
                // 背面只有一套攻击（素材限制，鸿蒙定案）：平A后直接回战斗待机；
                // 正面才有 Attack→Attack_Down 两连击
                if (combatView == "back") {
                    return ST_COMBAT_IDLE
                }
                return if (!hold) ST_ATTACK_DOWN else null
            }

            ST_SLEEP, ST_INTERACT, ST_SIT -> {
                // 无 hold：interact/sit 结束回基建待机；sleep 无 hold 冻结末帧
                if (!hold && state != ST_SLEEP) return ST_IDLE
                return null
            }

            ST_FLY_BEGIN -> {
                if (!hold) return if (activeSkill == "skill3") ST_FLY_IDLE else ST_FLY
                return null
            }

            ST_FLY_END -> {
                if (!hold) {
                    return when {
                        activeSkill == "skill3" -> ST_FLY_IDLE
                        combatMode -> ST_COMBAT_IDLE
                        else -> ST_IDLE
                    }
                }
                return null
            }

            ST_SKILL1_END, ST_SKILL2_END -> {
                if (!hold) return ST_COMBAT_IDLE
                return null
            }

            else -> {
                val nxt = STATE_CHAIN[state]
                if (nxt != null && !hold) {
                    // 技能已关闭时结束动画 → 战斗待机
                    if (activeSkill == null && nxt in setOf(ST_SKILL1_IDLE, ST_SKILL2_IDLE, ST_FLY_IDLE)) {
                        return ST_COMBAT_IDLE
                    }
                    // 一技能部署动画（带技能部署）结束 → 技能开启待机
                    if (state == ST_COMBAT_START2 && activeSkill == "skill1") {
                        return ST_SKILL1_IDLE
                    }
                    return nxt
                }
                return null
            }
        }
    }

    /** 单击（照 mouseReleaseEvent：动画播放中点击不打断不重置） */
    @Synchronized
    fun click() {
        if (state in setOf(ST_INTERACT, ST_SIT, ST_SLEEP, ST_MOVE) ||
            state in FLIGHT_STATES && state != ST_FLY_IDLE
        ) {
            // 动画播放中：不打断（原版语义）
            return
        }
        if (!combatMode) {
            setState(ST_INTERACT)
            return
        }
        when (activeSkill) {
            "skill3" -> {
                onAttack?.invoke()
                setState(ST_FLY_COMBAT)
            }
            "skill2" -> {
                onAttack?.invoke()
                setState(ST_SKILL2_LOOP)
            }
            "skill1" -> {
                onAttack?.invoke()
                setState(ST_SKILL1_LOOP)
            }
            else -> {
                onAttack?.invoke()
                setState(ST_ATTACK)
            }
        }
    }

    /** 技能开关（照 _toggle_skill） */
    @Synchronized
    fun toggleSkill(skillName: String) {
        if (activeSkill == skillName) {
            activeSkill = null
            when (skillName) {
                "skill3" -> setState(ST_FLY_END)
                "skill2" -> setState(ST_SKILL2_END)
                "skill1" -> setState(ST_SKILL1_END)
            }
        } else {
            activeSkill = skillName
            when (skillName) {
                "skill3" -> setState(ST_FLY_BEGIN)
                "skill2" -> setState(ST_SKILL2_BEGIN)
                "skill1" -> setState(ST_COMBAT_START2) // 部署动画（带技能）→ 链到技能待机
            }
        }
    }

    @Synchronized
    fun enterCombat() {
        combatMode = true
        activeSkill = null
        setState(ST_COMBAT_START)
    }

    @Synchronized
    fun exitCombat() {
        combatMode = false
        activeSkill = null
        setState(ST_IDLE)
    }

    /** 战斗视角切换（back/front） */
    @Synchronized
    fun toggleCombatView() {
        combatView = if (combatView == "front") "back" else "front"
        // 换视角重置动画时间（同动画重新开始）
        time = 0.0
    }
}
