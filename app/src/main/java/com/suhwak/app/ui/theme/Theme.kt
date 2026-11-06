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

// 과수원 테마 (형이 시안 C를 고름, 2026-11-06): 올리브 녹색 바탕, 짙은 녹색 글자, 열매만 주황.
val Blue = Color(0xFF4B6B2A)
private val Ink = Color(0xFF222B18)
private val Grey700 = Color(0xFF5A6448)
private val Grey500 = Color(0xFF6B7558)
private val Grey200 = Color(0xFFD3DCBE)
private val Grey100 = Color(0xFFEEF2E2)

/** 과수원 색 (예전 이름 Sea를 그대로 써서 화면 코드는 그대로 둔다). */
object Sea {
    val Ink = Color(0xFF222B18)
    val Soft = Color(0xFF5A6448)
    val Accent = Color(0xFF4B6B2A)
    val Mint = Color(0xFF6E8B3D)
    val Deep = Color(0xFF2F4419)
    val Sky = listOf(Color(0xFFEEF2E2), Color(0xFFE3EAD2))
    val Panel = Color(0xFFE3EAD2)
    val MintCard = Color(0xFFDCE6C4)
    val MintCardOpen = Color(0xFFC9DAA6)
    val Lavender = Color(0xFFF3EBD6)
    val Line = Color(0x594B6B2A)
    val Warm = Color(0xFFD9622B)
    val Danger = Color(0xFFC8372D)
    /** 열매(주황): 채운 모양, 글자용 진한 주황, 옅은 바탕. */
    val Fruit = Color(0xFFD9622B)
    val FruitText = Color(0xFFA9481B)
    val FruitSoft = Color(0xFFFBE3D5)
    /** 아직 안 딴 칸. */
    val Empty = Color(0xFFE3EAD2)
}

private val Light = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6C4),
    onPrimaryContainer = Color(0xFF2F4419),
    secondary = Grey700,
    secondaryContainer = Color(0xFFE3EAD2),
    onSecondaryContainer = Color(0xFF2F4419),
    tertiary = Color(0xFFA9481B),
    background = Grey100,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey700,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE3EAD2),
    outline = Grey500,
    outlineVariant = Grey200,
    error = Color(0xFFC8372D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C77E),
    onPrimary = Color(0xFF1C2A0C),
    primaryContainer = Color(0xFF3E5823),
    onPrimaryContainer = Color(0xFFDCE6C4),
    background = Color(0xFF12160D),
    onBackground = Color(0xFFEEF2E2),
    surface = Color(0xFF1C2214),
    onSurface = Color(0xFFEEF2E2),
    surfaceVariant = Color(0xFF2A3220),
    onSurfaceVariant = Color(0xFFBFC9AC),
    surfaceContainer = Color(0xFF1C2214),
    surfaceContainerLow = Color(0xFF1C2214),
    surfaceContainerHigh = Color(0xFF242B1A),
    surfaceContainerHighest = Color(0xFF2A3220),
    outline = Color(0xFF8E9878),
    outlineVariant = Color(0xFF3A4430),
    error = Color(0xFFF07A6E),
)

/** 제목 글꼴: 도현체 (SIL OFL, assets/DOHYEON_LICENSE.txt). 굵기는 하나뿐이라 Normal로만 쓴다. */
val DoHyeon = FontFamily(Font(R.font.dohyeon_regular, FontWeight.Normal))

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
private fun style(size: Int, weight: FontWeight, line: Int, family: FontFamily = Pretendard) =
    TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = maxOf(line / size.toFloat(), 1.35f).em,
        letterSpacing = 0.sp,
    )

private val AppTypography = Typography(
    displayLarge = style(40, FontWeight.Normal, 48, DoHyeon),
    displayMedium = style(34, FontWeight.Normal, 42, DoHyeon),
    displaySmall = style(30, FontWeight.Normal, 38, DoHyeon),
    headlineLarge = style(28, FontWeight.Normal, 36, DoHyeon),
    headlineMedium = style(24, FontWeight.Normal, 32, DoHyeon),
    headlineSmall = style(22, FontWeight.Normal, 30, DoHyeon),
    titleLarge = style(22, FontWeight.Normal, 30, DoHyeon),
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
