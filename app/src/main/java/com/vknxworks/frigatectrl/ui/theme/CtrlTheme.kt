package com.vknxworks.frigatectrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.R

// "Ctrl / Signal": the look every Ctrl app shares. Dark only, one accent, Space Grotesk for text and
// JetBrains Mono for numbers, labels and the wordmark. Both fonts are bundled (SIL OFL 1.1, see
// assets/licenses), nothing is downloaded.

/** Colour tokens. Change [Accent] to recolour the whole app. */
object CtrlColors {
    val Accent = Color(0xFFB48CFF)
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    /** Card borders. */
    val Outline = Color(0xFF232328)
    /** Borders on buttons, the avatar and other controls. */
    val ControlOutline = Color(0xFF2A2A2F)
    val Divider = Color(0xFF1E1E23)
    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    /** Disabled and stopped states. */
    val Inactive = Color(0xFF8C8C94)
    val Error = Color(0xFFFF6B6B)
    val ErrorContainer = Color(0xFF2A1517)

    val SwitchTrackOff = Color(0xFF2A2A30)
    val ProgressTrack = Color(0xFF26262C)
    val PinEmpty = Color(0xFF3A3A41)
    /** Semi-opaque background behind controls drawn over video. */
    val VideoScrim = Color(0xB808080A)
}

/** The two bundled typefaces. */
object CtrlFonts {
    val Grotesk = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
        // Space Grotesk has no static SemiBold; the variable font supplies weight 600.
        Font(R.font.space_grotesk_variable, FontWeight.SemiBold),
        Font(R.font.space_grotesk_bold, FontWeight.Bold),
    )
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
        Font(R.font.jetbrains_mono_semibold, FontWeight.Bold),
    )
}

/** Text styles outside the Material type scale. */
object CtrlType {
    /** Section labels: mono, 11 sp, wide tracking. Write the text in upper case. */
    val Label = TextStyle(fontFamily = CtrlFonts.Mono, fontSize = 11.sp, letterSpacing = 0.16.em, fontWeight = FontWeight.Normal)
    /** The "FRIGATE/CTRL" wordmark. */
    val Wordmark = TextStyle(fontFamily = CtrlFonts.Mono, fontSize = 13.sp, letterSpacing = 0.14.em, fontWeight = FontWeight.SemiBold)
    /** Small mono readouts (addresses, times, counts). */
    val Mono = TextStyle(fontFamily = CtrlFonts.Mono, fontSize = 12.sp, fontWeight = FontWeight.Normal)
    /** Giant hero numbers. */
    val HeroNumber = TextStyle(fontFamily = CtrlFonts.Grotesk, fontSize = 64.sp, letterSpacing = (-0.05).em, fontWeight = FontWeight.Bold, lineHeight = 60.sp)
}

private fun grotesk(size: Int, weight: FontWeight, tracking: Double = 0.0, line: Int? = null) = TextStyle(
    fontFamily = CtrlFonts.Grotesk,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = tracking.em,
    lineHeight = (line ?: (size * 1.3).toInt()).sp,
)

val CtrlTypography = Typography(
    displayLarge = grotesk(56, FontWeight.Bold, -0.04, 56),
    displayMedium = grotesk(44, FontWeight.Bold, -0.04, 46),
    displaySmall = grotesk(36, FontWeight.Bold, -0.035, 40),
    headlineLarge = grotesk(40, FontWeight.Bold, -0.035, 42),
    headlineMedium = grotesk(28, FontWeight.Bold, -0.03, 32),
    headlineSmall = grotesk(24, FontWeight.Bold, -0.02, 28),
    titleLarge = grotesk(20, FontWeight.SemiBold, -0.01),
    titleMedium = grotesk(18, FontWeight.SemiBold),
    titleSmall = grotesk(15, FontWeight.SemiBold),
    bodyLarge = grotesk(16, FontWeight.Normal),
    bodyMedium = grotesk(14, FontWeight.Normal),
    bodySmall = grotesk(12, FontWeight.Normal),
    labelLarge = grotesk(14, FontWeight.SemiBold),
    labelMedium = grotesk(12, FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = CtrlFonts.Mono, fontSize = 11.sp, letterSpacing = 0.12.em, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
)

/** Badges 10, controls 14–16, cards 22, hero cards, dialogs and sheets 26. */
val CtrlShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

object CtrlShape {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Tile = RoundedCornerShape(18.dp)
    val Control = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(18.dp)
    val Field = RoundedCornerShape(16.dp)
    val Badge = RoundedCornerShape(9.dp)
    val Dot = RoundedCornerShape(2.dp)
    val Sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
}

/** Material 3 colours mapped onto the tokens, so stock components pick them up too. */
val CtrlColorScheme: ColorScheme = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.OnAccent,
    secondaryContainer = CtrlColors.Raised,
    onSecondaryContainer = CtrlColors.Text,
    tertiary = CtrlColors.Accent,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.Text,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Surface,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Raised,
    surfaceContainerHighest = CtrlColors.Raised,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.Background,
    outline = CtrlColors.ControlOutline,
    outlineVariant = CtrlColors.Outline,
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = CtrlColors.ErrorContainer,
    onErrorContainer = CtrlColors.Error,
    scrim = Color.Black,
)
