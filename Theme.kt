package com.witnesscam

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0A0C0F)
val Panel = Color(0xFF14171C)
val PanelHi = Color(0xFF1D2229)
val Line = Color(0xFF262C34)
val Ink = Color(0xFFF2F4F7)
val InkDim = Color(0xFF8A94A1)
val Accent = Color(0xFFFF453A)
val Ok = Color(0xFF3DD68C)
val Warn = Color(0xFFF5B83D)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Bg,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
)

private val Sans = FontFamily.SansSerif

private val Type = Typography(
    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum",
    ),
)

@Composable
fun WitnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
