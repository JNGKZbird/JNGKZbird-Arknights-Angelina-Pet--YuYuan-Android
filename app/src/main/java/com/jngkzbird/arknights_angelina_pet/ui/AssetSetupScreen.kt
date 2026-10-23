package com.jngkzbird.arknights_angelina_pet.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jngkzbird.arknights_angelina_pet.R
import com.jngkzbird.arknights_angelina_pet.model.AssetCancelled
import com.jngkzbird.arknights_angelina_pet.model.AssetStore
import com.jngkzbird.arknights_angelina_pet.model.SettingsStore
import com.jngkzbird.arknights_angelina_pet.ui.theme.ThemeTokens
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private fun mb(bytes: Long): String =
    String.format(Locale.US, "%.1f", bytes / 1048576.0)

@Composable
private fun ThemePill(
    label: String,
    dotColor: Color,
    selected: Boolean,
    selectedBackground: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) selectedBackground else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(label, color = textColor, fontSize = 11.sp)
    }
}

/**
 * 首启阻断页：素材未安装时只显示这一页，安装完成后 recreate 进入正常界面。
 * 素材（Spine/语音）不随 APK 分发，版权归鹰角网络；本页只负责下载/导入与校验。
 */
@Composable
fun AssetSetupScreen(
    store: AssetStore,
    initialTheme: ThemeTokens,
    onReady: () -> Unit
) {
    val context = LocalContext.current
    val manifest = store.manifest
    val handler = remember { Handler(Looper.getMainLooper()) }
    val cancelled = remember { AtomicBoolean(false) }
    var theme by remember { mutableStateOf(initialTheme) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun setTheme(option: ThemeTokens) {
        theme = option
        SettingsStore.saveTheme(context, if (option.name == "酸橙味") "lime" else "sky")
    }

    fun installLocal(zip: File) {
        busy = true
        progress = 0f
        status = "正在校验并安装…"
        error = null
        Thread {
            try {
                store.installFromZip(zip, checkHash = true) { p ->
                    handler.post { progress = p / 100f }
                }
                handler.post { onReady() }
            } catch (e: Exception) {
                handler.post {
                    busy = false
                    status = ""
                    error = e.message ?: e.javaClass.simpleName
                }
            } finally {
                zip.delete()
            }
        }.start()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = "正在读取 zip…"
        error = null
        Thread {
            val temp = File(context.cacheDir, "manual_asset.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("无法读取所选文件")
                handler.post { installLocal(temp) }
            } catch (e: Exception) {
                temp.delete()
                handler.post {
                    busy = false
                    status = ""
                    error = e.message ?: e.javaClass.simpleName
                }
            }
        }.start()
    }

    fun startDownload() {
        if (busy) return
        busy = true
        progress = 0f
        status = "正在连接下载源…"
        error = null
        cancelled.set(false)
        Thread {
            val temp = File(context.cacheDir, "asset_download.zip")
            try {
                store.download(
                    temp,
                    onProgress = { done, total ->
                        handler.post {
                            progress = if (total > 0) {
                                (done.toFloat() / total).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            status = "正在下载… ${mb(done)} / ${mb(total)} MB"
                        }
                    },
                    cancelled = { cancelled.get() }
                )
                handler.post {
                    status = "正在校验并安装…"
                    progress = 1f
                }
                store.installFromZip(temp, checkHash = true)
                temp.delete()
                handler.post { onReady() }
            } catch (e: AssetCancelled) {
                temp.delete()
                handler.post {
                    busy = false
                    progress = 0f
                    status = "已取消下载。"
                    error = null
                }
            } catch (e: Exception) {
                temp.delete()
                handler.post {
                    busy = false
                    progress = 0f
                    status = ""
                    error = e.message ?: e.javaClass.simpleName
                }
            }
        }.start()
    }

    val bgHover by animateColorAsState(theme.bgHover, tween(300, easing = FastOutSlowInEasing), label = "bgHover")
    val accent by animateColorAsState(theme.accent, tween(300, easing = FastOutSlowInEasing), label = "accent")
    val accentSoft by animateColorAsState(theme.accentSoft, tween(300, easing = FastOutSlowInEasing), label = "accentSoft")
    val borderSubtle by animateColorAsState(theme.borderSubtle, tween(300, easing = FastOutSlowInEasing), label = "borderSubtle")
    val borderStrong by animateColorAsState(theme.borderStrong, tween(300, easing = FastOutSlowInEasing), label = "borderStrong")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bgHover),
        contentAlignment = Alignment.Center
    ) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val phoneScreen = LocalConfiguration.current.smallestScreenWidthDp < 600
        val shortLandscape = phoneScreen && widthDp > heightDp && heightDp < 600f
        val compact = widthDp < 600f || shortLandscape
        val medium = widthDp >= 600f && widthDp < 840f
        val dense = shortLandscape

        val cardFraction = when {
            dense -> 0.9f
            compact -> 0.92f
            medium -> 0.9f
            else -> 0.92f
        }
        val cardMaxWidth = when {
            dense -> 520.dp
            compact -> 540.dp
            medium -> 600.dp
            else -> 680.dp
        }
        val contentPad = if (compact) 18.dp else 28.dp
        val contentPadV = if (dense) 14.dp else if (compact) 18.dp else 24.dp
        val sectionGap = if (dense) 8.dp else if (compact) 10.dp else 14.dp
        val bandPadV = if (dense) 12.dp else if (compact) 16.dp else 22.dp
        val iconSize = if (dense) 44.dp else if (compact) 48.dp else 64.dp
        val titleSize = if (dense) 18.sp else if (compact) 19.sp else 22.sp
        val subtitleSize = if (dense) 10.sp else if (compact) 11.sp else 12.sp
        val smallSize = if (dense) 9.5.sp else if (compact) 10.sp else 11.sp
        val headlineSize = if (dense) 21.sp else if (compact) 22.sp else 26.sp
        val headlineLine = if (dense) 27.sp else if (compact) 28.sp else 34.sp
        val bodySize = if (dense) 12.5.sp else if (compact) 13.sp else 14.sp
        val bodyLine = if (dense) 18.sp else if (compact) 19.sp else 22.sp
        val labelSize = if (compact) 10.sp else 11.sp
        val valueSize = if (compact) 16.sp else 18.sp
        val primaryHeight = if (dense) 40.dp else if (compact) 42.dp else 48.dp
        val secondaryHeight = if (dense) 36.dp else if (compact) 38.dp else 44.dp
        val primaryText = if (compact) 15.sp else 16.sp
        val secondaryText = if (compact) 13.sp else 14.sp
        val footerSize = if (compact) 10.sp else 11.sp

        val cardShape = RoundedCornerShape(24.dp)
        val cardModifier = Modifier
            .fillMaxWidth(cardFraction)
            .widthIn(max = cardMaxWidth)
            .shadow(18.dp, cardShape)
            .clip(cardShape)
            .background(Color.White)
            .border(1.dp, borderSubtle, cardShape)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = if (compact) 12.dp else 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = cardModifier) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentSoft)
                        .padding(horizontal = contentPad, vertical = bandPadV),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.avatar),
                        contentDescription = null,
                        modifier = Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "酸橙味的信",
                            color = theme.textPrimary,
                            fontSize = titleSize,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${manifest.petName} · 角色素材",
                            color = theme.textSecondary,
                            fontSize = subtitleSize
                        )
                        Text(
                            "Spine 动画 · 中文/日文语音 · 技能图标",
                            color = theme.textTertiary,
                            fontSize = smallSize
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentPad, vertical = contentPadV),
                    verticalArrangement = Arrangement.spacedBy(sectionGap)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("界面主题", color = theme.textTertiary, fontSize = smallSize)
                        Spacer(Modifier.weight(1f))
                        ThemePill(
                            label = "天空",
                            dotColor = ThemeTokens.SKY.accent,
                            selected = theme.name == "天空",
                            selectedBackground = bgHover,
                            borderColor = borderSubtle,
                            textColor = if (theme.name == "天空") theme.textPrimary else theme.textTertiary,
                            onClick = { setTheme(ThemeTokens.SKY) }
                        )
                        Spacer(Modifier.width(8.dp))
                        ThemePill(
                            label = "酸橙味",
                            dotColor = ThemeTokens.LIME.accent,
                            selected = theme.name == "酸橙味",
                            selectedBackground = bgHover,
                            borderColor = borderSubtle,
                            textColor = if (theme.name == "酸橙味") theme.textPrimary else theme.textTertiary,
                            onClick = { setTheme(ThemeTokens.LIME) }
                        )
                    }

                    Text(
                        "给安洁莉娜写封信，\n她会来到你身边的。",
                        color = theme.textPrimary,
                        fontSize = headlineSize,
                        fontWeight = FontWeight.Medium,
                        lineHeight = headlineLine
                    )
                    Text(
                        "Spine 模型、语音与角色图标不随应用分发。下载一次，之后就能正常陪在你身边。",
                        color = theme.textBody,
                        fontSize = bodySize,
                        lineHeight = bodyLine
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgHover)
                            .padding(if (compact) 12.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("下载", color = theme.textTertiary, fontSize = labelSize)
                            Text(
                                "${mb(manifest.size)} MB",
                                color = theme.textPrimary,
                                fontSize = valueSize,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(if (compact) 28.dp else 32.dp)
                                .background(borderSubtle)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("安装后", color = theme.textTertiary, fontSize = labelSize)
                            Text(
                                "${mb(manifest.uncompressedSize)} MB",
                                color = theme.textPrimary,
                                fontSize = valueSize,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        "来源：GitHub Releases（官方素材库） · 整包 SHA-256 校验",
                        color = theme.textTertiary,
                        fontSize = smallSize
                    )

                    if (busy) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = accent,
                            trackColor = accentSoft
                        )
                        Text(status, color = theme.textSecondary, fontSize = if (compact) 12.sp else 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    cancelled.set(true)
                                    status = "正在取消…"
                                }
                            ) {
                                Text("取消下载", color = theme.textTertiary, fontSize = 13.sp)
                            }
                        }
                    }

                    error?.let { message ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF1F0))
                                .border(1.dp, Color(0xFFFAD2CF), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                "安装失败：$message",
                                color = Color(0xFFB3261E),
                                fontSize = if (compact) 12.sp else 13.sp,
                                lineHeight = if (compact) 18.sp else 19.sp
                            )
                        }
                    }

                    Button(
                        onClick = { startDownload() },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(primaryHeight),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("下载并安装", fontSize = primaryText)
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch("*/*") },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(secondaryHeight),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, borderStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBody)
                    ) {
                        Text("手动导入官方 zip", fontSize = secondaryText)
                    }

                    Text(
                        "手动导入同样校验整包 SHA-256；不是官方包会被拒绝",
                        color = theme.textTertiary,
                        fontSize = smallSize
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { (context as? Activity)?.finish() },
                            enabled = !busy
                        ) {
                            Text("退出", color = theme.textTertiary, fontSize = if (compact) 12.sp else 13.sp)
                        }
                    }

                    Text(
                        "素材版权归上海鹰角网络有限公司所有 · 仅供个人非商业使用",
                        color = theme.textQuaternary,
                        fontSize = footerSize,
                        lineHeight = if (compact) 14.sp else 16.sp
                    )
                }
            }
        }
    }
}
