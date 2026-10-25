package com.vknxworks.frigatectrl.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlFonts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun nowSec() = System.currentTimeMillis() / 1000.0

/** Mono chip drawn over video: light text on a dark scrim so it reads on any picture, with a square dot. */
@Composable
fun OverlayChip(
    text: String,
    dot: Color?,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(CtrlColors.VideoScrim)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            val a = if (pulse) {
                val t = rememberInfiniteTransition(label = "pulse")
                t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value
            } else 1f
            Box(Modifier.size(8.dp).alpha(a).clip(RoundedCornerShape(2.dp)).background(dot))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text, color = CtrlColors.OnVideo, fontFamily = CtrlFonts.Mono, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Keeps the display awake while [enabled]. */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Hides the status and navigation bars while [enabled] (swipe from an edge to peek). */
@Composable
fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled) {
            ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
