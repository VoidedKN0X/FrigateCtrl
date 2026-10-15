package com.vknxworks.frigateremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF0E1116)
    val Surface = Color(0xFF161B22)
    val SurfaceHi = Color(0xFF1F2630)
    val Line = Color(0xFF2B3440)
    val Text = Color(0xFFE8EDF3)
    val Muted = Color(0xFF8B96A5)
    val Accent = Color(0xFF3DDC97)      // recordings / brand
    val Alert = Color(0xFFFF5A5F)       // alert review items
    val Detection = Color(0xFFFFB547)   // detection review items
    val Motion = Color(0xFF5AA9FF)
}

@Composable
fun FrigateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color(0xFF00331F),
            background = Palette.Bg,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHi,
            onSurfaceVariant = Palette.Muted,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.SurfaceHi,
            surfaceContainerHighest = Palette.SurfaceHi,
            outline = Palette.Line,
            error = Palette.Alert,
        ),
        content = content,
    )
}
