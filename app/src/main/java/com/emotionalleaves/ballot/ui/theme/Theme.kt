package com.emotionalleaves.ballot.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A campaign poster crossed with a briefing folder: heavy type, hard edges, one loud color.
// No red-versus-blue coding anywhere. The signal color is the app's, not a party's.
val Ink = Color(0xFF0B0D12)
val Paper = Color(0xFFF6F4EF)
val Signal = Color(0xFFFF3B1F)
val Steel = Color(0xFF8A8F98)
val SteelDark = Color(0xFF5A606B)
val InkRaised = Color(0xFF161922)
val PaperSunk = Color(0xFFE9E6DE)
val Go = Color(0xFF2E9E6A)     // used only for "verified" and "done" markers, never for a party
val Caution = Color(0xFFE0A100)

data class BallotColors(val fg: Color, val bg: Color, val signal: Color, val muted: Color, val raised: Color, val rule: Color, val dark: Boolean)

val LocalBallotColors = staticCompositionLocalOf { BallotColors(Paper, Ink, Signal, Steel, InkRaised, Paper, true) }

private val condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
private val body = FontFamily(Typeface.create("sans-serif", Typeface.NORMAL))

val Headline = TextStyle(fontFamily = condensed, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
val Numbers = TextStyle(fontFamily = condensed, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum")

private val typography = Typography(
    displayLarge = Headline.copy(fontSize = 64.sp, lineHeight = 60.sp),
    displayMedium = Headline.copy(fontSize = 44.sp, lineHeight = 44.sp),
    displaySmall = Headline.copy(fontSize = 34.sp, lineHeight = 36.sp),
    headlineLarge = Headline.copy(fontSize = 30.sp, lineHeight = 32.sp),
    headlineMedium = Headline.copy(fontSize = 26.sp, lineHeight = 28.sp),
    headlineSmall = Headline.copy(fontSize = 22.sp, lineHeight = 24.sp),
    titleLarge = Headline.copy(fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = Headline.copy(fontSize = 17.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    titleSmall = Headline.copy(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp),
    bodyLarge = TextStyle(fontFamily = body, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = body, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = body, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = Headline.copy(fontSize = 15.sp, letterSpacing = 1.sp),
    labelMedium = Headline.copy(fontSize = 13.sp, letterSpacing = 1.sp),
    labelSmall = Headline.copy(fontSize = 11.sp, letterSpacing = 1.sp),
)

private val square = Shapes(
    extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp),
)

private fun darkScheme(): ColorScheme = darkColorScheme(
    primary = Signal, onPrimary = Ink, secondary = Paper, onSecondary = Ink, tertiary = Steel,
    background = Ink, onBackground = Paper, surface = Ink, onSurface = Paper, surfaceVariant = InkRaised, onSurfaceVariant = Steel,
    outline = Paper, outlineVariant = SteelDark, error = Signal, onError = Ink, surfaceContainer = InkRaised,
    surfaceContainerHigh = InkRaised, surfaceContainerHighest = InkRaised, surfaceContainerLow = Ink, surfaceContainerLowest = Ink,
)

private fun lightScheme(): ColorScheme = lightColorScheme(
    primary = Signal, onPrimary = Ink, secondary = Ink, onSecondary = Paper, tertiary = SteelDark,
    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink, surfaceVariant = PaperSunk, onSurfaceVariant = SteelDark,
    outline = Ink, outlineVariant = Steel, error = Signal, onError = Ink, surfaceContainer = PaperSunk,
    surfaceContainerHigh = PaperSunk, surfaceContainerHighest = PaperSunk, surfaceContainerLow = Paper, surfaceContainerLowest = Paper,
)

/** Dark by default, light for outdoor use at doors and events. Text scales with the user's setting. */
@Composable
fun BallotTheme(dark: Boolean = true, textScale: Float = 1f, content: @Composable () -> Unit) {
    val colors = if (dark) BallotColors(Paper, Ink, Signal, Steel, InkRaised, Paper, true)
    else BallotColors(Ink, Paper, Signal, SteelDark, PaperSunk, Ink, false)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalBallotColors provides colors,
        LocalDensity provides Density(density.density, density.fontScale * textScale),
    ) {
        MaterialTheme(colorScheme = if (dark) darkScheme() else lightScheme(), typography = typography, shapes = square, content = content)
    }
}
