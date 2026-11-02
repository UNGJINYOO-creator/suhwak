package com.suhwak.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.suhwak.app.R

// Pastel sea (형 chose ① 하늘·민트, 2026-10-26): pale sky and mint, soft blue accent, dark ink only for text,
// so the colours never pull the eye away from the words.
val Blue = Color(0xFF5A9BEE)
private val Ink = Color(0xFF2B3A4F)
private val Grey700 = Color(0xFF56657C)
private val Grey500 = Color(0xFF8A97AB)
private val Grey200 = Color(0xFFE1EAF4)
private val Grey100 = Color(0xFFF2F9FC)

/** Fixed pastel colours for the sea pieces (headers, game cards, panels). */
object Sea {
    val Ink = Color(0xFF2B3A4F)
    val Soft = Color(0xFF6E7D93)
    val Accent = Color(0xFF3A7BD5)
    val Mint = Color(0xFF3FA79C)
    val Sky = listOf(Color(0xFFDCEFFF), Color(0xFFD6F3F1))
    val Panel = Color(0xFFE6F2FF)
    val MintCard = Color(0xFFE2F5EF)
    val MintCardOpen = Color(0xFFCDEEE3)
    val Lavender = Color(0xFFE6E9FB)
    val Line = Color(0x598CB4DC)
    val Warm = Color(0xFFD9692B)
    val Danger = Color(0xFFE5534B)
}

private val Light = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F0FF),
    onPrimaryContainer = Color(0xFF2F5FA8),
    secondary = Grey700,
    secondaryContainer = Color(0xFFE2F5EF),
    onSecondaryContainer = Color(0xFF24493F),
    tertiary = Color(0xFF3F7FC9),
    background = Grey100,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey700,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEAF2FA),
    outline = Grey500,
    outlineVariant = Grey200,
    error = Color(0xFFF04452),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4593FC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B3A66),
    onPrimaryContainer = Color(0xFFC9E2FF),
    background = Color(0xFF101013),
    onBackground = Color(0xFFF2F4F6),
    surface = Color(0xFF1D1D21),
    onSurface = Color(0xFFF2F4F6),
    surfaceVariant = Color(0xFF2C2C33),
    onSurfaceVariant = Color(0xFFB0B8C1),
    surfaceContainer = Color(0xFF1D1D21),
    surfaceContainerLow = Color(0xFF1D1D21),
    surfaceContainerHigh = Color(0xFF26262B),
    surfaceContainerHighest = Color(0xFF2C2C33),
    outline = Color(0xFF8B95A1),
    outlineVariant = Color(0xFF3A3A42),
    error = Color(0xFFF66570),
)

@OptIn(ExperimentalTextApi::class)
private fun pretendard(weight: FontWeight) =
    Font(R.font.pretendard_variable, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Pretendard = FontFamily(
    pretendard(FontWeight.Normal),
    pretendard(FontWeight.Medium),
    pretendard(FontWeight.SemiBold),
    pretendard(FontWeight.Bold),
)

// Body text gets about 1.6× line height so Korean explanations do not feel cramped.
// Line height in em, so text drawn bigger than its style (card words, meanings) gets taller lines too
// instead of a second line running into the first.
private fun style(size: Int, weight: FontWeight, line: Int) =
    TextStyle(
        fontFamily = Pretendard,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = maxOf(line / size.toFloat(), 1.35f).em,
        letterSpacing = 0.sp,
    )

private val AppTypography = Typography(
    displayLarge = style(40, FontWeight.Bold, 48),
    displayMedium = style(34, FontWeight.Bold, 42),
    displaySmall = style(30, FontWeight.Bold, 38),
    headlineLarge = style(28, FontWeight.Bold, 36),
    headlineMedium = style(24, FontWeight.Bold, 32),
    headlineSmall = style(22, FontWeight.Bold, 30),
    titleLarge = style(22, FontWeight.Bold, 30),
    titleMedium = style(17, FontWeight.SemiBold, 25),
    titleSmall = style(15, FontWeight.SemiBold, 23),
    bodyLarge = style(16, FontWeight.Normal, 26),
    bodyMedium = style(15, FontWeight.Normal, 24),
    bodySmall = style(13, FontWeight.Normal, 21),
    labelLarge = style(15, FontWeight.SemiBold, 20),
    labelMedium = style(13, FontWeight.Medium, 19),
    labelSmall = style(12, FontWeight.Medium, 17),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WordBookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
