package com.codyphil.liftlog.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Volt = Color(0xFFC6FF3D)
    val VoltDim = Color(0xFF8DBA1F)
    val Ember = Color(0xFFFF6B35)
    val Ice = Color(0xFF3DD9FF)
    val Ink = Color(0xFF0A0C0F)
    val Panel = Color(0xFF14171C)
    val PanelHigh = Color(0xFF1C2027)
    val PanelHigher = Color(0xFF252A33)
    val Line = Color(0xFF2E343E)
    val TextHi = Color(0xFFF2F4F7)
    val TextMid = Color(0xFFA3ABB8)
    val TextLow = Color(0xFF6B7380)
    val Danger = Color(0xFFFF4D5E)

    val heroGradient = Brush.linearGradient(listOf(Color(0xFF1F2A10), Color(0xFF12202A)))
    val voltGradient = Brush.linearGradient(listOf(Volt, Ice))
    val emberGradient = Brush.linearGradient(listOf(Ember, Color(0xFFFFB13D)))
}

private val colors = darkColorScheme(
    primary = Brand.Volt,
    onPrimary = Brand.Ink,
    primaryContainer = Color(0xFF2B3A0E),
    onPrimaryContainer = Brand.Volt,
    secondary = Brand.Ember,
    onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF3A1E12),
    onSecondaryContainer = Color(0xFFFFB59A),
    tertiary = Brand.Ice,
    onTertiary = Brand.Ink,
    background = Brand.Ink,
    onBackground = Brand.TextHi,
    surface = Brand.Ink,
    onSurface = Brand.TextHi,
    surfaceVariant = Brand.PanelHigh,
    onSurfaceVariant = Brand.TextMid,
    surfaceContainerLowest = Brand.Ink,
    surfaceContainerLow = Brand.Panel,
    surfaceContainer = Brand.Panel,
    surfaceContainerHigh = Brand.PanelHigh,
    surfaceContainerHighest = Brand.PanelHigher,
    outline = Brand.Line,
    outlineVariant = Color(0xFF22272F),
    error = Brand.Danger,
    onError = Brand.Ink,
)

private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

/** Tabular figures so changing numbers don't jitter. */
val Numeric = TextStyle(fontFeatureSettings = "tnum")

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun LiftLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
