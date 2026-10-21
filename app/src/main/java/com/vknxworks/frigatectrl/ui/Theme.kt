package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.data.ThemeMode

/** Fixed meaning colours (timeline, badges). Tuned per light/dark so they stay legible. */
@Immutable
data class StatusColors(
    val recording: Color,
    val motion: Color,
    val detection: Color,
    val alert: Color,
    val live: Color,
)

private val DarkStatus = StatusColors(
    recording = Color(0xFF4FD8A4),
    motion = Color(0xFF7AB8FF),
    detection = Color(0xFFFFC266),
    alert = Color(0xFFFF6B6B),
    live = Color(0xFFFF5252),
)
private val LightStatus = StatusColors(
    recording = Color(0xFF00875A),
    motion = Color(0xFF1E6FD9),
    detection = Color(0xFFC77700),
    alert = Color(0xFFD32F2F),
    live = Color(0xFFE53935),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

/** Shortcut: `Status.alert` etc. inside composables. */
val Status: StatusColors
    @Composable get() = LocalStatusColors.current

// Brand fallback when Material You isn't available or is turned off (seed ≈ teal green).
private val BrandDark = darkColorScheme(
    primary = Color(0xFF5ADBA8),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005137),
    onPrimaryContainer = Color(0xFF79F8C3),
    secondary = Color(0xFFB3CCBF),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFA5CCE0),
    onTertiary = Color(0xFF063544),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E8FC),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
)

private val BrandLight = lightColorScheme(
    primary = Color(0xFF006C4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8BF8C6),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3D6374),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC1E8FC),
    onTertiaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF5FBF6),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF6),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F0),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE4EAE5),
    surfaceContainerHighest = Color(0xFFDEE4DF),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C1),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)

@Composable
fun isAppInDarkTheme(): Boolean = when (App.settings.theme) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark, ThemeMode.Black -> true
}

@Composable
fun FrigateTheme(content: @Composable () -> Unit) {
    val s = App.settings
    val dark = isAppInDarkTheme()
    val ctx = LocalContext.current
    var scheme = when {
        s.dynamicColor ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> BrandDark
        else -> BrandLight
    }
    if (dark && s.pureBlack) scheme = scheme.pureBlack()

    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
    }
}
