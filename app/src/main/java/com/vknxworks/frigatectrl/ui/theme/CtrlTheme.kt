package com.vknxworks.frigatectrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.R

// "Ctrl / Signal": the look every Ctrl app shares. Follows the phone's dark / light setting, one accent,
// Space Grotesk for text and JetBrains Mono for numbers, labels and the wordmark. Both fonts are bundled
// (SIL OFL 1.1, see assets/licenses), nothing is downloaded.

/** Every colour of one mode (Signal Dark or Signal Light). */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    /** Card borders. */
    val outline: Color,
    /** Borders on buttons, the avatar and other controls. */
    val controlOutline: Color,
    val divider: Color,
    val text: Color,
    val muted: Color,
    /** Disabled and stopped states. */
    val inactive: Color,
    /** The accent used for text and icons on the background (the fill colour is [CtrlColors.Accent]). */
    val accentText: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val switchTrackOff: Color,
    val switchKnobOff: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val pinEmpty: Color,
)

/**
 * Colour tokens. [Accent] is the one accent fill for the whole app: change it here to recolour everything.
 * The other tokens read the current mode's [CtrlPalette] ([Dark] or [Light]), chosen by [FrigateTheme]
 * from the phone's dark-theme setting, so they work in composables, modifiers and draw code alike.
 */
object CtrlColors {
    /** Accent fill: hero cards, primary buttons, the selected tab, switch tracks, PIN dots. Same in both modes. */
    val Accent = Color(0xFFB48CFF)
    /** Content on the accent fill. */
    val OnAccent = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        outline = Color(0xFF232328),
        controlOutline = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        inactive = Color(0xFF8C8C94),
        accentText = Accent,
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        errorContainer = Color(0xFF2A1517),
        switchTrackOff = Color(0xFF2A2A30),
        switchKnobOff = Color(0xFF8C8C94),
        progressTrack = Color(0xFF26262C),
        progressFill = Color(0xFFF4F4F2),
        pinEmpty = Color(0xFF3A3A41),
    )

    val Light = CtrlPalette(
        isDark = false,
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        outline = Color(0xFFDEDED7),
        controlOutline = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        inactive = Color(0xFF74747C),
        // The fill accent is too pale for text on white.
        accentText = Color(0xFF6A3BDB),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE9E9),
        switchTrackOff = Color(0xFFDCDCD5),
        switchKnobOff = Color(0xFF74747C),
        progressTrack = Color(0xFFE2E2DC),
        progressFill = Color(0xFF0B0B0D),
        pinEmpty = Color(0xFFC9C9C1),
    )

    /** The current mode's palette; set by [FrigateTheme]. Snapshot state, so changes redraw everything. */
    var palette: CtrlPalette by mutableStateOf(Dark)
        internal set

    val Background: Color get() = palette.background
    val Surface: Color get() = palette.surface
    val Raised: Color get() = palette.raised
    val Outline: Color get() = palette.outline
    val ControlOutline: Color get() = palette.controlOutline
    val Divider: Color get() = palette.divider
    val Text: Color get() = palette.text
    val Muted: Color get() = palette.muted
    val Inactive: Color get() = palette.inactive
    /** Accent for text, icons, links and focused borders on the background. */
    val AccentText: Color get() = palette.accentText
    val Error: Color get() = palette.error
    val OnError: Color get() = palette.onError
    val ErrorContainer: Color get() = palette.errorContainer
    val SwitchTrackOff: Color get() = palette.switchTrackOff
    val SwitchKnobOff: Color get() = palette.switchKnobOff
    val ProgressTrack: Color get() = palette.progressTrack
    val ProgressFill: Color get() = palette.progressFill
    val PinEmpty: Color get() = palette.pinEmpty

    /** Video, camera pictures and the controls drawn over them stay dark in both modes. */
    val VideoScrim = Color(0xB808080A)
    val OnVideo = Color(0xFFF4F4F2)
    val VideoBackdrop = Color(0xFF08080A)
}

/** The current palette, for components that prefer a CompositionLocal. Provided by [FrigateTheme]. */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

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

/** Material 3 colours mapped onto a palette, so stock components pick them up too. */
fun ctrlColorScheme(p: CtrlPalette): ColorScheme {
    // Stock components mostly use primary for text and indicators: the readable accent in light mode.
    val primary = if (p.isDark) CtrlColors.Accent else p.accentText
    val onPrimary = if (p.isDark) CtrlColors.OnAccent else Color.White
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = CtrlColors.Accent,
        onPrimaryContainer = CtrlColors.OnAccent,
        inversePrimary = CtrlColors.Accent,
        secondary = p.text,
        onSecondary = p.background,
        secondaryContainer = p.raised,
        onSecondaryContainer = p.text,
        tertiary = p.accentText,
        onTertiary = onPrimary,
        tertiaryContainer = p.raised,
        onTertiaryContainer = p.text,
        background = p.background,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent,
        surfaceBright = p.raised,
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.raised,
        surfaceContainerHighest = p.raised,
        inverseSurface = p.text,
        inverseOnSurface = p.background,
        outline = p.controlOutline,
        outlineVariant = p.outline,
        error = p.error,
        onError = p.onError,
        errorContainer = p.errorContainer,
        onErrorContainer = p.error,
        scrim = Color.Black,
    )
}
