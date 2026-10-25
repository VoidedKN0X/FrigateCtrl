package com.vknxworks.frigatectrl.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vknxworks.frigatectrl.ui.theme.CtrlColorScheme
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
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

private val SignalStatus = StatusColors(
    recording = CtrlColors.Accent,
    motion = Color(0xFF7AB8FF),
    detection = Color(0xFFFFC266),
    alert = CtrlColors.Error,
    live = CtrlColors.Accent,
)

val LocalStatusColors = staticCompositionLocalOf { SignalStatus }

/** Shortcut: `Status.alert` etc. inside composables. */
val Status: StatusColors
    @Composable get() = LocalStatusColors.current

/** The Ctrl / Signal theme: dark only, no wallpaper colours. */
@Composable
fun FrigateTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides SignalStatus) {
        MaterialTheme(colorScheme = CtrlColorScheme, shapes = CtrlShapes, typography = CtrlTypography) {
            CompositionLocalProvider(LocalTextStyle provides CtrlTypography.bodyLarge, content = content)
        }
    }
}
