package com.jngkzbird.arknights_angelina_pet

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jngkzbird.arknights_angelina_pet.model.ChatApi
import com.jngkzbird.arknights_angelina_pet.model.ChatStore
import com.jngkzbird.arknights_angelina_pet.model.SettingsStore
import com.jngkzbird.arknights_angelina_pet.ui.PetGLTextureView
import com.jngkzbird.arknights_angelina_pet.ui.theme.AngelinaPetTheme
import com.jngkzbird.arknights_angelina_pet.ui.theme.ThemeTokens
import com.jngkzbird.arknights_angelina_pet.ui.terminal.TerminalScreen

class MainActivity : ComponentActivity() {
    /** 崩溃捕获：真机/卓易通无 adb——①堆栈写公共下载目录 ②异常类型直接弹 Toast（系统渲染，进程死后仍可见） */
    private fun installCrashLogger() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            val text = "thread=${t.name}\n" + sw.toString()
            android.util.Log.e("PetCrash", text)
            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "petcrash-" + System.currentTimeMillis() + ".txt")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                } else {
                    java.io.File(getExternalFilesDir(null), "crash.txt").writeText(text)
                }
            } catch (_: Exception) {
                try {
                    java.io.File(getExternalFilesDir(null), "crash.txt").writeText(text)
                } catch (_: Exception) {
                }
            }
            // 应用内部文件（最可靠）：下次启动时弹窗展示完整堆栈
            try {
                java.io.File(filesDir, "last_crash.txt").writeText(text)
            } catch (_: Exception) {
            }
            // 屏幕上直接显示崩溃类型（用户口述回传即可）
            val msg = "崩溃：" + (e.javaClass.simpleName ?: "未知异常") + "｜" + (e.message ?: "")
            try {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
                    prev?.uncaughtException(t, e)
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
                        prev?.uncaughtException(t, e)
                    }
                }
            } catch (_: Exception) {
                prev?.uncaughtException(t, e)
            }
        }
    }

    /** 上次崩溃的完整堆栈（installCrashLogger 写入；启动时弹窗展示便于回传） */
    private fun readLastCrash(): String? {
        return try {
            val f = java.io.File(filesDir, "last_crash.txt")
            if (f.exists()) f.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    // ── 旋转锁定（与鸿蒙同方案）：锁定=冻结当前方向；解锁=跟随传感器 ──
    private var isLandscapeNow = false
    private var rotationObserver: android.database.ContentObserver? = null
    private var rotationPolling = true
    private val rotationPollHandler = Handler(Looper.getMainLooper())
    private var appliedLockedState = -1 // -1=未知 0=锁定 1=解锁（幂等去重）

    private fun applyRotationLock() {
        try {
            val locked = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
            val target = if (locked) 0 else 1
            if (target == appliedLockedState) {
                return
            }
            appliedLockedState = target
            requestedOrientation = if (locked) {
                // 具体方向值不受系统旋转锁覆盖（冻结当前方向，不被拽回竖屏）
                if (isLandscapeNow) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
        } catch (_: Exception) {
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isLandscapeNow = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun onDestroy() {
        super.onDestroy()
        rotationPolling = false
        rotationObserver?.let { contentResolver.unregisterContentObserver(it) }
        rotationObserver = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        enableEdgeToEdge()
        // 旋转锁定：观察者快响应 + 1.5s 轮询兜底（卓易通容器下设置项可能不实时广播）
        isLandscapeNow = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        rotationObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                applyRotationLock()
            }
        }
        try {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), true, rotationObserver!!
            )
        } catch (_: Exception) {
        }
        applyRotationLock()
        rotationPollHandler.postDelayed(object : Runnable {
            override fun run() {
                if (rotationPolling) {
                    applyRotationLock()
                    rotationPollHandler.postDelayed(this, 1500)
                }
            }
        }, 1500)
        // 全屏沉浸（鸿蒙同款）：隐藏状态栏与导航栏，边缘滑动可临时唤出
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent {
            AngelinaPetTheme {
                var theme by remember { mutableStateOf(if (SettingsStore.loadTheme(this@MainActivity) == "lime") ThemeTokens.LIME else ThemeTokens.SKY) }
                var history by remember { mutableStateOf(ChatStore.loadHistory(this@MainActivity)) }
                var historyVersion by remember { mutableStateOf(0) }
                var settings by remember { mutableStateOf(SettingsStore.loadSettings(this@MainActivity)) }
                var streamVersion by remember { mutableStateOf(0) }
                val api = remember { ChatApi() }
                val agentPrompt = remember {
                    fun loadSkill(name: String): String = try {
                        assets.open("skill/$name").use { it.readBytes().toString(Charsets.UTF_8) }
                    } catch (e: Exception) {
                        ""
                    }
                    ChatStore.defaultAgentPrompt = loadSkill("angelina_bundle.md")
                    ChatStore.priestessPrompt = loadSkill("priestess_bundle.md")
                    ChatStore.jieerpeitaPrompt = loadSkill("jieerpeita_bundle.md")
                    ChatStore.angelinaPastPrompt = loadSkill("angelina_past_bundle.md")
                    ChatStore.defaultAgentPrompt
                }
                val petView = remember { PetGLTextureView(this@MainActivity) }
                var companionMode by remember { mutableStateOf(false) }
                val lastCrashText = remember { readLastCrash() }
                var crashDialogShown by remember { mutableStateOf(lastCrashText != null) }
                val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) {
                        companionMode = true
                    } else {
                        petView.showChatBubble("未获得相机权限，无法进入陪伴模式", 3000)
                    }
                }
                // 菜单「陪伴/退出陪伴」：先申请相机权限（用户主动点击后申请，合规）
                petView.onCompanionToggle = {
                    if (companionMode) {
                        companionMode = false
                    } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        companionMode = true
                    } else {
                        camPermLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
                petView.companionActive = companionMode
                val config = LocalConfiguration.current
                val isPhone = config.smallestScreenWidthDp < 600
                val isLandscape = config.screenWidthDp > config.screenHeightDp
                // 设置变化 → 桌宠语音联动
                petView.applySettings(settings)
                Box(modifier = Modifier.fillMaxSize()) {
                    if (companionMode) {
                        // 陪伴模式：CameraX 实时背景（cover 裁剪）+ 桌宠悬浮；终端隐藏
                        val context = this@MainActivity
                        val lifecycleOwner = LocalLifecycleOwner.current
                        val previewView = remember {
                            PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                        }
                        DisposableEffect(Unit) {
                            val future = ProcessCameraProvider.getInstance(context)
                            val listener = Runnable {
                                try {
                                    val provider = future.get()
                                    val preview = Preview.Builder().build()
                                    preview.setSurfaceProvider(previewView.surfaceProvider)
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                                } catch (_: Exception) {
                                    // 无可用相机（模拟器未开虚拟摄像头等）：背景保持透明
                                }
                            }
                            future.addListener(listener, ContextCompat.getMainExecutor(context))
                            onDispose {
                                future.addListener({
                                    try {
                                        future.get().unbindAll()
                                    } catch (_: Exception) {
                                    }
                                }, ContextCompat.getMainExecutor(context))
                            }
                        }
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    } else {
                        // 终端 UI 在下层，桌宠 TextureView（透明纹理）在上层
                        TerminalScreen(
                            theme, history, historyVersion, settings, agentPrompt, streamVersion, api,
                            isPhone, isLandscape,
                            onBubble = { msg, ms -> petView.showChatBubble(msg, ms) },
                            onBubbleStream = { delta -> petView.appendChatBubble(delta) },
                            onMutate = {
                                historyVersion++
                                streamVersion++
                                ChatStore.saveHistory(this@MainActivity, history)
                            },
                            onToggleTheme = {
                                theme = if (theme == ThemeTokens.SKY) ThemeTokens.LIME else ThemeTokens.SKY
                                SettingsStore.saveTheme(this@MainActivity, if (theme == ThemeTokens.SKY) "sky" else "lime")
                            },
                            onSaveSettings = { s ->
                                settings = s
                                SettingsStore.saveSettings(this@MainActivity, s)
                            }
                        )
                    }
                    AndroidView(factory = { petView }, modifier = Modifier.fillMaxSize())
                    // 上次崩溃的完整堆栈弹窗（点击「知道了」清除记录）
                    if (crashDialogShown && lastCrashText != null) {
                        AlertDialog(
                            onDismissRequest = { crashDialogShown = false },
                            title = { Text("上次崩溃信息") },
                            text = { Text(lastCrashText.take(1200), fontSize = 11.sp) },
                            confirmButton = {
                                Text(
                                    "知道了",
                                    color = androidx.compose.ui.graphics.Color(0xFF6E9BF2),
                                    modifier = Modifier
                                        .clickable {
                                            crashDialogShown = false
                                            try {
                                                java.io.File(filesDir, "last_crash.txt").delete()
                                            } catch (_: Exception) {
                                            }
                                        }
                                        .padding(12.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
