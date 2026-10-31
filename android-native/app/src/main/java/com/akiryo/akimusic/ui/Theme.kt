package com.akiryo.akimusic.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.akiryo.akimusic.R

/** 品牌色：Apple Music 红 */
val Accent = Color(0xFFFA2D48)
val AccentSoft = Color(0xFFFF5B6E)

/** 全局字体：Inter */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** 语义色板，随深浅色切换 */
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    val glassTint: Color,
    val glassAlpha: Float,
    val isDark: Boolean,
)

private val LightPalette = Palette(
    background = Color(0xFFF6F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0B0B0D),
    textSecondary = Color(0xFF8A8A8E),
    textTertiary = Color(0xFFB0B0B5),
    separator = Color(0x1A000000),
    glassTint = Color(0xFFFFFFFF),
    glassAlpha = 0.72f,
    isDark = false,
)

private val DarkPalette = Palette(
    background = Color(0xFF0B0B0F),
    surface = Color(0xFF15151A),
    surfaceElevated = Color(0xFF1C1C22),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF9B9BA3),
    textTertiary = Color(0xFF6A6A72),
    separator = Color(0x1FFFFFFF),
    glassTint = Color(0xFF1E1E24),
    glassAlpha = 0.74f,
    isDark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.sp),
)

@Composable
fun AkiTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = Accent,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = Accent,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
