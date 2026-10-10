package com.substichoose.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colors from the approved mockups (docs/CONTRACTS.md, Part C). Light theme only. */
object SC {
    val Background = Color(0xFFF5F6F1)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF18201B)
    val Muted = Color(0xFF55605A)
    val Accent = Color(0xFF2F6B3F)
    val AccentDark = Color(0xFF1F5130)
    val Border = Color(0xFFE1E4DC)
    val BorderStrong = Color(0xFFD9DDD3)
    val Divider = Color(0xFFEEF0EA)

    val AllergenBg = Color(0xFFFFE8D9)
    val AllergenText = Color(0xFF9A3412)
    val SafeBg = Color(0xFFE1F0E3)
    val SafeText = Color(0xFF1F5130)
    val SwapBg = Color(0xFFE4ECF7)
    val SwapText = Color(0xFF1E4A7A)

    /** Red flag for allergen rows on the Review screen. */
    val DangerBg = Color(0xFFFDE4E1)
    val Danger = Color(0xFFB42318)
}

private val Colors = lightColorScheme(
    primary = SC.Accent,
    onPrimary = Color.White,
    primaryContainer = SC.SafeBg,
    onPrimaryContainer = SC.SafeText,
    secondary = SC.Ink,
    onSecondary = Color.White,
    background = SC.Background,
    onBackground = SC.Ink,
    surface = SC.Card,
    onSurface = SC.Ink,
    surfaceVariant = SC.Background,
    onSurfaceVariant = SC.Muted,
    surfaceContainerLow = SC.Card,
    surfaceContainer = SC.Card,
    surfaceContainerHigh = SC.Card,
    outline = SC.BorderStrong,
    outlineVariant = SC.Border,
    error = SC.Danger,
    onError = Color.White,
    errorContainer = SC.DangerBg,
    onErrorContainer = SC.Danger,
)

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Sans, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
)

@Composable
fun SubstiChooseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
