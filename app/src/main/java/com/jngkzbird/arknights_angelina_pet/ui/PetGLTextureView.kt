package com.jngkzbird.arknights_angelina_pet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.jngkzbird.arknights_angelina_pet.engine.LayoutSpec
import com.jngkzbird.arknights_angelina_pet.engine.ModelLayout
import com.jngkzbird.arknights_angelina_pet.engine.PetEngine
import com.jngkzbird.arknights_angelina_pet.engine.ST_IDLE
import com.jngkzbird.arknights_angelina_pet.engine.ST_MOVE
import com.jngkzbird.arknights_angelina_pet.engine.ST_SIT
import com.jngkzbird.arknights_angelina_pet.engine.ST_SLEEP
import com.jngkzbird.arknights_angelina_pet.engine.computeModelLayout
import com.jngkzbird.arknights_angelina_pet.engine.finalizeLayouts
import com.jngkzbird.arknights_angelina_pet.gl.PetGLRenderer
import com.jngkzbird.arknights_angelina_pet.spine38.AtlasAttachmentLoaderImpl
import com.jngkzbird.arknights_angelina_pet.spine38.RenderTransform
import com.jngkzbird.arknights_angelina_pet.spine38.Skeleton
import com.jngkzbird.arknights_angelina_pet.spine38.SkeletonBinary
import com.jngkzbird.arknights_angelina_pet.spine38.SkeletonData
import com.jngkzbird.arknights_angelina_pet.spine38.TextureAtlas
import com.jngkzbird.arknights_angelina_pet.spine38.apply_animation
import com.jngkzbird.arknights_angelina_pet.spine38.collect_triangles

/**
 * 桌宠视图：FrameLayout 容器 = GL TextureView（全屏透明）+ 菜单子视图。
 * 菜单作为容器后添加的子视图参与标准视图层级命中测试（TextureView 之上，触摸可靠）。
 * 交互（照鸿蒙版定案）：单击=互动（乐观单击，播放中不打断）/双击=菜单/长按拖拽移动转向。
 */
class PetGLTextureView(context: Context) : FrameLayout(context), TextureView.SurfaceTextureListener {
    companion object {
        private const val TAG = "PetGL"
        private const val DOUBLE_TAP_MS = 300L
        private const val LONG_PRESS_MS = 500L
    }

    /** 单模型素材 */
    private class ModelAssets(
        val sd: SkeletonData,
        val bitmap: Bitmap
    )

    // ── GL 层 ──
    private val glView: TextureView = TextureView(context)

    // ── 跨 surface 共享的素材缓存 ──
    private val modelCache = HashMap<String, ModelAssets>()
    // 布局（启动时全部模型加载后计算 + 归一化）
    private val layouts = HashMap<String, ModelLayout>()

    // ── 引擎 ──
    private val engine = PetEngine()

    // 尺寸档（规格书）：手机 0.6 / 平板 0.9（基准 366dp 高）
    private var sizeTier = 0.6

    // ── 当前 surface 代状态（仅渲染线程访问） ──
    private var renderer: PetGLRenderer? = null
    private var renderThread: HandlerThread? = null
    private var running = false
    private var skeleton: Skeleton? = null
    private var currentModel: String = ""
    private var layoutBounds = HashMap<String, DoubleArray>()

    // ── 交互状态 ──
    private var charPosX = 0.0 // 角色参考点（setup 中心屏幕坐标），拖拽更新
    private var charPosY = 0.0
    private var posInitialized = false
    private var facingRight = true
    private var dragging = false
    private var lastTapTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastDragX = 0f
    private var initialDragCharX = 0.0
    private var initialDragCharY = 0.0
    private val uiHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { onLongPressed() }

    // ── 语音与字幕 ──
    private val voice = VoicePlayer(context)
    private var subtitleView: TextView? = null
    private var idleVoicePlayed = false

    // ── 陪伴模式（MainActivity 注入：菜单项状态与切换回调） ──
    var companionActive = false
    var onCompanionToggle: (() -> Unit)? = null

    init {
        glView.surfaceTextureListener = this
        glView.isOpaque = false
        addView(glView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        glView.setOnTouchListener { _, event -> handleTouch(event) }
        voice.init()
        voice.onSubtitle = { text, ms -> showSubtitle(text, ms) }
        // 启动问候（500ms 后播放日期问候）
        uiHandler.postDelayed({
            voice.play(voice.greetingVoiceName())
        }, 500L)
        // 闲置语音：启动 60 秒后才首次检查
        uiHandler.postDelayed({ scheduleIdleVoice() }, 60000L)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 角色位置尽早初始化（渲染线程三模型加载较慢）——否则启动问候字幕会落在左上角
        if (!posInitialized && w > 0 && h > 0) {
            charPosX = w / 2.0
            charPosY = h * 0.32
            posInitialized = true
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surface.setDefaultBufferSize(width, height)
        val density = resources.displayMetrics.density
        val minDp = minOf(width, height) / density
        sizeTier = if (minDp >= 720.0) 0.9 else 0.6
        val thread = HandlerThread("PetGLRender")
        thread.start()
        renderThread = thread
        val handler = Handler(thread.looper)
        handler.post {
            val r = PetGLRenderer()
            if (!r.init(Surface(surface))) {
                Log.e(TAG, "GL init failed")
                return@post
            }
            renderer = r
            running = true
            // 启动加载全部三模型 → 布局计算 + 跨模型归一化（全局统一画布）
            for (model in listOf("build", "back", "front")) {
                loadModelAssets(model)
            }
            computeLayouts()
            // 布局就绪后重定位已显示的字幕/气泡（启动问候可能先于布局出现）
            uiHandler.post {
                if (subtitleView != null) {
                    positionSubtitle()
                }
            }
            currentModel = engine.modelFor()
            val assets = loadModelAssets(currentModel)
            skeleton = Skeleton(assets.sd)
            skeleton!!.set_to_setup_pose()
            skeleton!!.update_world_transform()
            layoutBounds[currentModel] = skeleton!!.get_bounds()
            if (!posInitialized) {
                charPosX = width / 2.0
                charPosY = height * 0.32
                posInitialized = true
            }
            r.uploadTexture(assets.bitmap)
            var lastNanos = 0L
            val choreographer = Choreographer.getInstance()
            val frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!running) {
                        return
                    }
                    if (lastNanos == 0L) {
                        lastNanos = frameTimeNanos
                    }
                    val dt = ((frameTimeNanos - lastNanos) / 1e9).coerceIn(0.0, 0.1)
                    lastNanos = frameTimeNanos
                    drawFrame(dt)
                    choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(frameCallback)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        surface.setDefaultBufferSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        running = false
        val r = renderer
        val looper = renderThread?.looper
        renderer = null
        renderThread = null
        skeleton = null
        if (looper != null) {
            Handler(looper).post {
                r?.release()
                Looper.myLooper()?.quitSafely()
            }
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    // ── 素材加载（按模型缓存；可在渲染线程调用） ──
    private fun loadModelAssets(model: String): ModelAssets {
        modelCache[model]?.let { return it }
        val assets = context.assets
        val dir = "spine/$model"
        val skelName = if (model == "build") "build_char_1015_aglna2.skel" else "char_1015_aglna2.skel"
        val atlasName = if (model == "build") "build_char_1015_aglna2.atlas" else "char_1015_aglna2.atlas"
        val pngName = if (model == "build") "build_char_1015_aglna2.png" else "char_1015_aglna2.png"
        val skelBytes = assets.open("$dir/$skelName").use { it.readBytes() }
        val atlasText = assets.open("$dir/$atlasName").use { it.readBytes().toString(Charsets.UTF_8) }
        val bitmap = BitmapFactory.decodeStream(assets.open("$dir/$pngName"))
        val atlas = TextureAtlas(atlasText) { bitmap }
        val sd = SkeletonBinary(AtlasAttachmentLoaderImpl(atlas)).read_skeleton_data(skelBytes)
        val ma = ModelAssets(sd, bitmap)
        modelCache[model] = ma
        return ma
    }

    // 布局计算 + 跨模型归一化（照鸿蒙终局：全局统一画布）
    private fun computeLayouts() {
        val skillAnims = listOf(
            "Start_2", "Skill_1_Idle", "Skill_1_Loop", "Skill_1_End",
            "Skill_2_Begin", "Skill_2_Takeoff_Begin", "Skill_2_Takeoff_Loop", "Skill_2_Takeoff_End",
            "Skill_2_Idle", "Skill_2_Loop", "Skill_2_End",
            "Skill_3_Begin", "Skill_3_Idle", "Skill_3_Combat", "Skill_3_End"
        )
        val specs = mapOf(
            "build" to LayoutSpec(
                "Relax", "Interact", emptyList(),
                listOf("Relax", "Sit", "Sleep"), listOf("Sit", "Sleep"), 130.0, emptyList()
            ),
            "back" to LayoutSpec(
                "Idle", null, listOf("Attack"), emptyList(), emptyList(), 0.0, skillAnims
            ),
            "front" to LayoutSpec(
                "Idle", null, listOf("Attack", "Attack_Down"), emptyList(), emptyList(), 0.0, skillAnims
            )
        )
        for ((model, spec) in specs) {
            layouts[model] = computeModelLayout(modelCache[model]!!.sd, spec)
        }
        finalizeLayouts(listOf(layouts["build"]!!, layouts["back"]!!, layouts["front"]!!))
    }

    // ── 渲染线程：每帧 ──
    private fun drawFrame(dt: Double) {
        val r = renderer ?: return
        val sk = skeleton ?: return
        val model = engine.modelFor()
        if (model != currentModel) {
            // 状态机要求切换模型
            currentModel = model
            val assets = loadModelAssets(model)
            skeleton = Skeleton(assets.sd)
            skeleton!!.set_to_setup_pose()
            skeleton!!.update_world_transform()
            layoutBounds[model] = skeleton!!.get_bounds()
            r.uploadTexture(assets.bitmap)
        }
        val skNow = skeleton!!
        if (engine.tick(dt, skNow.data)) {
            // 状态转移导致模型可能变化，下一帧处理
        }
        val animName = engine.animFor()
        val anim = skNow.data.find_animation(animName) ?: return
        skNow.set_to_setup_pose()
        apply_animation(
            anim, skNow, engine.time,
            engine.state in com.jngkzbird.arknights_angelina_pet.engine.LOOP_STATES || engine.hold, 1.0
        )
        skNow.update_world_transform()

        val vpW = width.toDouble()
        val vpH = height.toDouble()
        val layout = layouts[model] ?: return
        // 特判动画（坐下/睡觉）用居中变换，其余用固定布局
        val st = layout.stateTransforms[animName]
        // 显示倍率 = 尺寸档 × 密度（画布单位 → 屏幕像素）
        val k = sizeTier * resources.displayMetrics.density
        val s = (st?.scale ?: layout.scale) * k
        val ctx = (st?.tx ?: layout.tx) * k
        val cty = (st?.ty ?: layout.ty) * k
        val anchorSk = layout.anchorX * s
        val transform = RenderTransform()
        transform.scale = s
        // 身体轴固定在 charPosX；镜像 = 只翻 x 轴
        if (facingRight) {
            transform.tx = charPosX - anchorSk
        } else {
            transform.scaleX = -s
            transform.tx = charPosX + anchorSk
        }
        // 画布合成：ty = vpH - charPosY - charCenterPad×k + ct.ty×k
        transform.ty = vpH - charPosY - layout.charCenterPad * k + cty

        val batch = collect_triangles(skNow, vpW, vpH, transform)
        r.drawFrame(batch, modelCache[model]!!.bitmap, vpW, vpH)
    }

    // ── 交互 ──
    private var gestureHit = false // DOWN 是否命中角色（未命中则整个手势忽略）

    private fun handleTouch(event: MotionEvent): Boolean {
        // 菜单开启时：整个触摸流都消费（否则 DOWN 未消费会中断流，下部菜单项收不到 UP）
        val menu = menuView
        if (menu != null) {
            if (event.actionMasked == MotionEvent.ACTION_UP && !dragging) {
                val item = menuHitTest(event.x, event.y)
                if (item != null) {
                    dismissMenu()
                    item.second()
                } else {
                    dismissMenu()
                    lastTapTime = 0L
                }
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 像素级命中测试：未命中角色则忽略整个手势
                gestureHit = com.jngkzbird.arknights_angelina_pet.spine38.hit_test_point(
                    event.x.toDouble(), event.y.toDouble()
                )
                if (!gestureHit) {
                    return false
                }
                downX = event.x
                downY = event.y
                lastDragX = event.x
                dragging = false
                uiHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!gestureHit) {
                    return false
                }
                if (dragging) {
                    val dx = (event.x - lastDragX).toDouble()
                    lastDragX = event.x
                    // 全方向跟手；转身只跟水平帧间增量
                    charPosX = (initialDragCharX + (event.x - downX).toDouble())
                        .coerceIn(0.0, maxOf(0.0, width.toDouble()))
                    charPosY = (initialDragCharY + (event.y - downY).toDouble())
                        .coerceIn(0.0, maxOf(0.0, height.toDouble()))
                    if (dx < -1) facingRight = false
                    else if (dx > 1) facingRight = true
                    positionSubtitle()
                    return true
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(longPressRunnable)
                if (!gestureHit) {
                    return false
                }
                if (dragging) {
                    // 松手不碰状态机：拖拽期间动画一直持续，松手自然续播（连贯稳定）
                    dragging = false
                    return true
                }
                val now = System.currentTimeMillis()
                if (now - lastTapTime < DOUBLE_TAP_MS) {
                    lastTapTime = 0L
                    showMenu(event.x, event.y)
                } else {
                    lastTapTime = now
                    engine.click() // 乐观单击：零延迟
                    voice.voiceClick(engine.combatMode, engine.activeSkill != null)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                uiHandler.removeCallbacks(longPressRunnable)
                if (dragging && gestureHit) {
                    dragging = false
                }
                return true
            }
        }
        return false
    }

    /** 菜单项命中：按 TextView 子视图实际矩形（分隔线跳过） */
    private fun menuHitTest(x: Float, y: Float): Pair<String, () -> Unit>? {
        val menu = menuView ?: return null
        val localX = x - menu.left
        val localY = y - menu.top
        var actionIdx = 0
        for (i in 0 until menu.childCount) {
            val child = menu.getChildAt(i)
            if (child !is TextView) {
                continue
            }
            if (localX >= child.left && localX <= child.right &&
                localY >= child.top && localY <= child.bottom
            ) {
                return if (actionIdx < menuActions.size) menuActions[actionIdx] else null
            }
            actionIdx++
        }
        return null
    }

    private fun onLongPressed() {
        dragging = true
        initialDragCharX = charPosX
        initialDragCharY = charPosY
        // 拖拽全程不碰状态机：动画在拖动中持续，松手自然续播
    }

    // ── 双击菜单（视觉用 FrameLayout 子视图；触摸由 handleTouch 按矩形分发） ──
    private var menuView: LinearLayout? = null
    private var menuActions: List<Pair<String, () -> Unit>> = emptyList()

    private fun showMenu(x: Float, y: Float) {
        dismissMenu()
        val density = resources.displayMetrics.density
        val menu = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // 鸿蒙版菜单面板：#D91A1A28 + borderRadius(14)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xD91A1A28.toInt())
                cornerRadius = 14f * density
            }
            elevation = 12f * density
            val pad = (4 * density).toInt()
            setPadding(0, pad, 0, pad)
        }
        val actions = ArrayList<Pair<String, () -> Unit>?>()

        fun addItem(label: String, active: Boolean, activeColor: Int, action: () -> Unit) {
            val tv = TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(if (active) activeColor else 0xE6FFFFFF.toInt())
                gravity = Gravity.CENTER
            }
            menu.addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (36 * density).toInt()
            ))
            actions.add(label to action)
        }

        fun addDivider(strong: Boolean) {
            val dv = android.view.View(context).apply {
                setBackgroundColor((if (strong) 0x55FFFFFF else 0x2EFFFFFF).toInt())
            }
            menu.addView(
                dv, LinearLayout.LayoutParams(
                    ((if (strong) 88 * 0.9f else 88 * 0.8f) * density).toInt(),
                    maxOf(1, (0.5f * density).toInt())
                ).apply { gravity = Gravity.CENTER_HORIZONTAL }
            )
            actions.add(null)
        }

        val inCombat = engine.combatMode
        // 状态区（基建模型：待机/坐下/睡觉）
        if (!inCombat) {
            addItem("待机", engine.state == ST_IDLE, 0xFFFFD080.toInt()) { engine.setState(ST_IDLE) }
            addDivider(false)
            addItem("坐下", engine.state == ST_SIT, 0xFFFFD080.toInt()) { engine.setState(ST_SIT, hold = true) }
            addDivider(false)
            addItem("睡觉", engine.state == ST_SLEEP, 0xFFFFD080.toInt()) { engine.setState(ST_SLEEP, hold = true) }
            addDivider(true)
        }
        // 模式区
        addItem("基建", !inCombat, 0xFFFFD080.toInt()) {
            if (inCombat) {
                engine.exitCombat()
                voice.play("任命助理")
            }
        }
        addDivider(false)
        addItem("战斗", inCombat, 0xFFFFD080.toInt()) {
            if (!inCombat) {
                engine.enterCombat()
                voice.play(if (Math.random() < 0.5) "部署1" else "部署2")
            }
        }
        addDivider(true)
        // 陪伴（CameraX 实时背景由 MainActivity 控制）
        addItem(if (companionActive) "退出陪伴" else "陪伴", companionActive, 0xFFFFD080.toInt()) {
            onCompanionToggle?.invoke()
        }
        if (inCombat) {
            addDivider(true)
            // 视角区
            addItem("正面", engine.combatView == "front", 0xFFFFD080.toInt()) {
                if (engine.combatView != "front") engine.toggleCombatView()
            }
            addDivider(false)
            addItem("背面", engine.combatView == "back", 0xFFFFD080.toInt()) {
                if (engine.combatView != "back") engine.toggleCombatView()
            }
            addDivider(true)
            // 技能区（开关式，选中=绿）
            for ((label, skill) in listOf("技能1" to "skill1", "技能2" to "skill2", "技能3" to "skill3")) {
                addItem(label, engine.activeSkill == skill, 0xFF8FFF8F.toInt()) { engine.toggleSkill(skill) }
                if (skill != "skill3") {
                    addDivider(false)
                }
            }
        }
        val lp = LayoutParams(
            (88 * density).toInt(), LayoutParams.WRAP_CONTENT
        ).apply {
            // 角色右侧（菜单贴身体）
            leftMargin = (charPosX + 150 * density).toInt()
            topMargin = (charPosY - 130 * density).toInt()
        }
        addView(menu, lp)
        menuView = menu
        menuActions = actions.filterNotNull()
    }

    private fun dismissMenu() {
        menuView?.let { removeView(it) }
        menuView = null
        menuActions = emptyList()
    }

    // ── 字幕气泡（跟随角色，自动消失） ──
    private fun showSubtitle(text: String, ms: Long) {
        dismissSubtitle()
        val density = resources.displayMetrics.density
        val tv = TextView(context).apply {
            this.text = text
            textSize = 13f
            // Windows 版 SubtitleWindow 同款：深色半透明圆角框 + 白字
            setTextColor(0xF5FFFFFF.toInt())
            val gd = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xD712121A.toInt()) // QColor(18,18,26,215)
                cornerRadius = 10f * density
            }
            background = gd
            val pad = (12 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, (8 * density).toInt())
            maxWidth = (420 * density).toInt() // Windows max_width 420
            elevation = 4f * density
        }
        addView(tv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        subtitleView = tv
        uiHandler.postDelayed({ dismissSubtitle() }, ms)
        positionSubtitle()
    }

    /** 直聊/信使气泡：设置文本与展示时长 */
    fun showChatBubble(msg: String, ms: Long) {
        showSubtitle(msg, ms)
    }

    /** 安洁莉娜直聊：LLM 流式追加到气泡（取消自动消失，直到 dismissChatBubble） */
    fun appendChatBubble(delta: String) {
        val tv = subtitleView
        if (tv == null) {
            showSubtitle(delta, Long.MAX_VALUE)
        } else {
            tv.text = (tv.text.toString() + delta)
            positionSubtitle()
        }
    }

    fun dismissChatBubble() {
        dismissSubtitle()
    }

    private fun dismissSubtitle() {
        subtitleView?.let { removeView(it) }
        subtitleView = null
    }

    private fun positionSubtitle() {
        val tv = subtitleView ?: return
        // 气泡宽度：252dp 上限（用户定案 420×0.6）且不超过屏幕
        tv.maxWidth = minOf(
            (252 * resources.displayMetrics.density).toInt(),
            width - (16 * resources.displayMetrics.density).toInt()
        )
        val k = sizeTier * resources.displayMetrics.density
        // 布局未就绪（启动初期）用兜底半高；布局算完后会重定位
        val layout = layouts[engine.modelFor()]
        val halfH = if (layout != null) layout.charHalfH * k else 150.0 * resources.displayMetrics.density
        tv.measure(
            android.view.View.MeasureSpec.UNSPECIFIED,
            android.view.View.MeasureSpec.UNSPECIFIED
        )
        val lp = tv.layoutParams as LayoutParams
        // 字幕可能比屏幕还宽（真机窄屏+420dp 上限压不住长文本）：上界先钳制再 coerceIn，防空区间崩溃
        val maxLeft = (width - tv.measuredWidth - 4).coerceAtLeast(4)
        lp.leftMargin = (charPosX - tv.measuredWidth / 2.0).toInt().coerceIn(4, maxLeft)
        lp.topMargin = (charPosY - halfH - 16 * resources.displayMetrics.density - tv.measuredHeight).toInt()
            .coerceAtLeast(4)
        tv.layoutParams = lp
    }

    /** 设置联动：语音开关/语言（主界面保存设置后调用） */
    fun applySettings(settings: com.jngkzbird.arknights_angelina_pet.model.ChatSettings) {
        voice.setEnabled(settings.voice_enabled)
        voice.setLanguage(settings.voice_language)
    }

    // ── 闲置语音（启动 60 秒后播一次；字幕可见→15s 重试；拖动/非循环/战斗→60s 重试） ──
    private fun scheduleIdleVoice() {
        if (idleVoicePlayed) {
            return
        }
        if (subtitleView != null) {
            uiHandler.postDelayed({ scheduleIdleVoice() }, 15000L)
            return
        }
        val inBaseLoop = !engine.combatMode && !dragging &&
            engine.state in com.jngkzbird.arknights_angelina_pet.engine.LOOP_STATES
        if (!inBaseLoop) {
            uiHandler.postDelayed({ scheduleIdleVoice() }, 60000L)
            return
        }
        voice.play("闲置", 8000L)
        idleVoicePlayed = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        voice.release()
    }
}
