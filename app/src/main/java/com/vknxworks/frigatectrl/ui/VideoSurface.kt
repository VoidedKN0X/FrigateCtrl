package com.vknxworks.frigatectrl.ui

import android.view.LayoutInflater
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.vknxworks.frigatectrl.R
import kotlinx.coroutines.launch

const val MAX_ZOOM = 8f

@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    val zoomed get() = scale > 1.01f

    fun reset() { scale = 1f; offset = Offset.Zero }

    internal fun clamp(size: IntSize) {
        val maxX = (scale - 1f) * size.width / 2f
        val maxY = (scale - 1f) * size.height / 2f
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
}

/**
 * Pinch to zoom (up to 8×), drag to pan while zoomed, double-tap to zoom in on a spot or back out.
 * Single-finger drags pass through when not zoomed.
 */
fun Modifier.zoomable(state: ZoomState, onTap: (() -> Unit)? = null): Modifier = this
    .clipToBounds()
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            while (true) {
                val ev = awaitPointerEvent()
                val pressed = ev.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed > 1 || state.zoomed) {
                    val zoom = ev.calculateZoom()
                    val pan = ev.calculatePan()
                    val centroid = ev.calculateCentroid(useCurrent = true)
                    val newScale = (state.scale * zoom).coerceIn(1f, MAX_ZOOM)
                    // Keep the point under the fingers fixed while zooming.
                    val c = centroid - Offset(size.width / 2f, size.height / 2f)
                    val factor = newScale / state.scale
                    state.offset = (state.offset - c) * factor + c + pan
                    state.scale = newScale
                    if (newScale <= 1f) state.offset = Offset.Zero
                    state.clamp(size)
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        }
    }
    .pointerInput(state, onTap) {
        detectTapGestures(
            onTap = { onTap?.invoke() },
            onDoubleTap = { p ->
                if (state.zoomed) state.reset()
                else {
                    val s = 2.5f
                    val c = p - Offset(size.width / 2f, size.height / 2f)
                    state.scale = s
                    state.offset = -c * (s - 1f)
                    state.clamp(size)
                }
            },
        )
    }

/** Media3 player drawn on a TextureView, so pinch-zoom transforms apply to the picture. */
@Composable
fun PlayerSurface(player: Player?, zoom: ZoomState, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> LayoutInflater.from(ctx).inflate(R.layout.player_texture, null) as PlayerView },
        update = { it.player = player },
        modifier = modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom.scale
            scaleY = zoom.scale
            translationX = zoom.offset.x
            translationY = zoom.offset.y
        },
    )
}
