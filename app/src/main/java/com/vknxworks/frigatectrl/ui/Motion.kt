package com.vknxworks.frigatectrl.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first

/** Springy shrink while pressed — makes cards and buttons feel physical. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.96f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Waits while the app is in the background (stopped), so repeating refreshes don't keep asking the server
 * while you're not looking. Picture-in-picture counts as on screen.
 */
suspend fun Lifecycle.awaitStarted() {
    currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
}
