package com.emotionalleaves.ballot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Calm and clear: soft paper, ink text, one quiet accent, sentence case, rounded cards, plenty of
 * room. Built for long use without fatigue. No red-versus-blue coding anywhere: the accent is a
 * deep teal that belongs to no party.
 */

private val PaperL = Color(0xFFF7F6F3)
private val CardL = Color(0xFFFFFFFF)
private val TextL = Color(0xFF1C1E21)
private val MutedL = Color(0xFF62666D)
private val LineL = Color(0xFFE3E1DC)
private val AccentL = Color(0xFF0E7C6B)
private val AccentSoftL = Color(0xFFDDF0EC)
private val OnAccentSoftL = Color(0xFF0B3B33)

private val PaperD = Color(0xFF15171A)
private val CardD = Color(0xFF1E2125)
private val TextD = Color(0xFFECEDEE)
private val MutedD = Color(0xFF9BA1A8)
private val LineD = Color(0xFF2C3036)
private val AccentD = Color(0xFF5CC8B4)
private val AccentSoftD = Color(0xFF173A35)
private val OnAccentSoftD = Color(0xFFD5F2EC)

/** Marks for done and verified, and for things that need attention. Never used for parties. */
val Go = Color(0xFF2E8B57)
val Caution = Color(0xFFB7791F)

/**
 * fg: text. bg: screen. signal: the accent. muted: secondary text. raised: cards. rule: hairlines.
 * accentSoft/onAccent: highlighted cards and the text on them. warn: errors. onSignal: text on the accent.
 */
data class BallotColors(
    val fg: Color, val bg: Color, val signal: Color, val muted: Color, val raised: Color, val rule: Color, val dark: Boolean,
    val accentSoft: Color, val onAccent: Color, val warn: Color, val onSignal: Color,
)

private val lightColors = BallotColors(TextL, PaperL, AccentL, MutedL, CardL, LineL, false, AccentSoftL, OnAccentSoftL, Color(0xFFB3261E), Color.White)
private val darkColors = BallotColors(TextD, PaperD, AccentD, MutedD, CardD, LineD, true, AccentSoftD, OnAccentSoftD, Color(0xFFF2B8B5), Color(0xFF0B1F1C))

val LocalBallotColors = staticCompositionLocalOf { lightColors }

/** Text color on a highlighted card, in either theme. */
val Ink: Color @Composable get() = LocalBallotColors.current.onAccent

/** The accent color, in either theme. */
val Signal: Color @Composable get() = LocalBallotColors.current.signal

private val sans = FontFamily.SansSerif

val Headline = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
val Numbers = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

private val typography = Typography(
    displayLarge = Headline.copy(fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = Headline.copy(fontSize = 32.sp, lineHeight = 38.sp),
    displaySmall = Headline.copy(fontSize = 26.sp, lineHeight = 32.sp),
    headlineLarge = Headline.copy(fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = Headline.copy(fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = Headline.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = Headline.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = Headline.copy(fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = Headline.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = sans, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val rounded = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

private fun lightScheme(): ColorScheme = lightColorScheme(
    primary = AccentL, onPrimary = Color.White, primaryContainer = AccentSoftL, onPrimaryContainer = OnAccentSoftL,
    secondary = AccentL, onSecondary = Color.White, tertiary = MutedL,
    background = PaperL, onBackground = TextL, surface = PaperL, onSurface = TextL, surfaceVariant = CardL, onSurfaceVariant = MutedL,
    outline = LineL, outlineVariant = LineL, error = Color(0xFFB3261E), onError = Color.White,
    surfaceContainer = CardL, surfaceContainerHigh = CardL, surfaceContainerHighest = CardL, surfaceContainerLow = PaperL, surfaceContainerLowest = PaperL,
)

private fun darkScheme(): ColorScheme = darkColorScheme(
    primary = AccentD, onPrimary = Color(0xFF0B1F1C), primaryContainer = AccentSoftD, onPrimaryContainer = OnAccentSoftD,
    secondary = AccentD, onSecondary = Color(0xFF0B1F1C), tertiary = MutedD,
    background = PaperD, onBackground = TextD, surface = PaperD, onSurface = TextD, surfaceVariant = CardD, onSurfaceVariant = MutedD,
    outline = LineD, outlineVariant = LineD, error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    surfaceContainer = CardD, surfaceContainerHigh = CardD, surfaceContainerHighest = CardD, surfaceContainerLow = PaperD, surfaceContainerLowest = PaperD,
)

/** Light by default; dark for night use. Text scales with the user's setting. */
@Composable
fun BallotTheme(dark: Boolean = false, textScale: Float = 1f, content: @Composable () -> Unit) {
    val colors = if (dark) darkColors else lightColors
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalBallotColors provides colors,
        LocalDensity provides Density(density.density, density.fontScale * textScale),
    ) {
        MaterialTheme(colorScheme = if (dark) darkScheme() else lightScheme(), typography = typography, shapes = rounded, content = content)
    }
}
