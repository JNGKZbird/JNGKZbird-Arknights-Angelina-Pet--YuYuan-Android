package com.jngkzbird.arknights_angelina_pet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 天空淡蓝 #6E9BF2（双主题之一，另一为酸橙味 #F09A4A，后续设置面板接入）
private val SkyBlue = Color(0xFF6E9BF2)
private val LimeOrange = Color(0xFFF09A4A)

private val LightColors = lightColorScheme(
    primary = SkyBlue,
    secondary = LimeOrange
)

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    secondary = LimeOrange
)

@Composable
fun AngelinaPetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
