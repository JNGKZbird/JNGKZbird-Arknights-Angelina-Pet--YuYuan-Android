package com.jngkzbird.arknights_angelina_pet.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题令牌 — 鸿蒙版 ThemeTokens 移植（Kimi K3 设计系统）。
 * 唯一事实来源：单一强调色/五级灰阶/圆角四档/唯一阴影（仅输入卡片）。禁止自由发挥。
 */
data class ThemeTokens(
    val name: String,
    val bgMain: Color,
    val bgSidebar: Color,
    val bgHover: Color,
    val bgActive: Color,
    val bubbleUser: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textBody: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val accent: Color,
    val accentHover: Color,
    val accentSoft: Color,
    val accentBorder: Color,
    val sendIdle: Color
) {
    companion object {
        // 天空主题：淡蓝强调色（少女飞行时天空与云朵的浪漫感）
        val SKY = ThemeTokens(
            name = "天空",
            bgMain = c("FFFFFF"), bgSidebar = c("FFFFFF"),
            bgHover = c("EFF4FC"), bgActive = c("E4EDFA"), bubbleUser = c("EFF4FC"),
            borderSubtle = c("E6EBF3"), borderStrong = c("D4DCE8"),
            textPrimary = c("1F2329"), textBody = c("3F454D"),
            textSecondary = c("6B7280"), textTertiary = c("9AA1AD"), textQuaternary = c("B6BCC7"),
            accent = c("6E9BF2"), accentHover = c("5C8CE8"),
            accentSoft = c("EAF2FE"), accentBorder = c("AEC8F2"), sendIdle = c("D6E3FB")
        )

        // 酸橙味主题：纯白打底，强调色=明亮亮橙（非米黄）
        val LIME = ThemeTokens(
            name = "酸橙味",
            bgMain = c("FFFFFF"), bgSidebar = c("FFFFFF"),
            bgHover = c("FFF4EA"), bgActive = c("FFE9D6"), bubbleUser = c("FFF3E6"),
            borderSubtle = c("F2E8DC"), borderStrong = c("E4D2BC"),
            textPrimary = c("1F2329"), textBody = c("3F454D"),
            textSecondary = c("6B7280"), textTertiary = c("9AA1AD"), textQuaternary = c("B6BCC7"),
            accent = c("FF8C00"), accentHover = c("F07D00"),
            accentSoft = c("FFF0E0"), accentBorder = c("FFB066"), sendIdle = c("FFD9AE")
        )

        private fun c(hex: String): Color = Color(hex.toLong(16) or 0xFF000000L)
    }
}
