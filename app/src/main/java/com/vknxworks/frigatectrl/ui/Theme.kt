package com.vknxworks.frigatectrl.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.LocalCtrlColors
import com.vknxworks.frigatectrl.ui.theme.ctrlColorScheme
import com.vknxworks.frigatectrl.ui.theme.CtrlShapes
import com.vknxworks.frigatectrl.ui.theme.CtrlTypography

/** Fixed meaning colours (timeline, badges). */
@Immutable
data class StatusColors(
    val recording: Color,
    val motion: Color,
    val detection: Color,
    val alert: Color,
    val live: Color,
)

private val DarkStatus = StatusColors(
    recording = CtrlColors.Accent,
    motion = Color(0xFF7AB8FF),
    detection = Color(0xFFFFC266),
    alert = CtrlColors.Dark.error,
    live = CtrlColors.Accent,
)

// Darker meaning colours on light backgrounds so they stay readable as text.
private val LightStatus = StatusColors(
    recording = CtrlColors.Light.accentText,
    motion = Color(0xFF1E6FD9),
    detection = Color(0xFFB86E00),
    alert = CtrlColors.Light.error,
    live = CtrlColors.Accent,
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

/** Shortcut: `Status.alert` etc. inside composables. */
val Status: StatusColors
    @Composable get() = LocalStatusColors.current

/**
 * The Ctrl / Signal theme. Follows the phone's dark / light setting (no in-app picker, no wallpaper
 * colours): Signal Dark or Signal Light.
 */
@Composable
fun FrigateTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    // Set before anything below reads it, so the whole tree draws with the right palette.
    if (CtrlColors.palette != palette) CtrlColors.palette = palette
    val scheme = remember(palette) { ctrlColorScheme(palette) }
    CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
        LocalCtrlColors provides palette,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = CtrlShapes, typography = CtrlTypography) {
            CompositionLocalProvider(LocalTextStyle provides CtrlTypography.bodyLarge, content = content)
        }
    }
}
