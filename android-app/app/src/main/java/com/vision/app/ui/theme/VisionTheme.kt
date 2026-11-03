package com.vision.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object VisionColors {
    val Background = Color(0xFF171615)
    val Surface = Color(0xFF292725)
    val SurfaceRaised = Color(0xFF34312E)
    val Cream = Color(0xFFFFF2E4)
    val MutedCream = Color(0xFFE4D7C9)
    val Outline = Color(0xFF645C55)
    val CharcoalText = Background
}

object VisionSpacing {
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 36.dp
}

private val VisionColorScheme = darkColorScheme(
    primary = VisionColors.Cream,
    onPrimary = VisionColors.CharcoalText,
    background = VisionColors.Background,
    onBackground = VisionColors.Cream,
    surface = VisionColors.Surface,
    onSurface = VisionColors.Cream,
    surfaceVariant = VisionColors.SurfaceRaised,
    onSurfaceVariant = VisionColors.MutedCream,
    outline = VisionColors.Outline,
)

// Inclusive Sans is not bundled until the actual licensed font files are supplied.
private val VisionTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun VisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VisionColorScheme, typography = VisionTypography, content = content)
}
