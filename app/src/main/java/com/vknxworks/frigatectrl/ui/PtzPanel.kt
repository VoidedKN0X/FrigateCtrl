package com.vknxworks.frigatectrl.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.vknxworks.frigatectrl.R
import androidx.compose.foundation.border
import com.vknxworks.frigatectrl.ui.components.CtrlChip
import com.vknxworks.frigatectrl.ui.theme.CtrlColors

/** Horizontally scrolling preset chips. */
@Composable
fun PtzPresets(presets: List<String>, send: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        items(presets) { p ->
            CtrlChip(p.replace('_', ' '), selected = false, onClick = { send("preset_$p") }, icon = Icons.Outlined.Bookmark)
        }
    }
}

/**
 * Pan/tilt joystick. Drag (or press an edge) toward a direction to move, release to stop.
 * Sends MOVE_UP/DOWN/LEFT/RIGHT on direction changes and STOP when let go.
 */
@Composable
fun PtzJoystick(send: (String) -> Unit, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val cs = MaterialTheme.colorScheme
    val sendCmd by rememberUpdatedState(send)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val knob = 56.dp
    val travel = with(density) { ((size - knob) / 2).toPx() }
    val thumb = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dir by remember { mutableStateOf<String?>(null) }

    Box(
        modifier.size(size).clip(CircleShape).background(CtrlColors.Raised).border(1.dp, CtrlColors.ControlOutline, CircleShape)
            .pointerInput(Unit) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val dead = travel * 0.28f
                fun dirFor(o: Offset): String? = when {
                    hypot(o.x, o.y) < dead -> null
                    abs(o.x) > abs(o.y) -> if (o.x > 0) "MOVE_RIGHT" else "MOVE_LEFT"
                    else -> if (o.y > 0) "MOVE_DOWN" else "MOVE_UP"
                }
                fun clamp(o: Offset): Offset {
                    val d = hypot(o.x, o.y)
                    return if (d <= travel) o else o * (travel / d)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var cur = clamp(down.position - center)
                    scope.launch { thumb.snapTo(cur) }
                    fun update() {
                        val nd = dirFor(cur)
                        if (nd != dir) {
                            if (dir != null) sendCmd("STOP")
                            if (nd != null) {
                                sendCmd(nd)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            dir = nd
                        }
                    }
                    update()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        cur = clamp(ch.position - center)
                        scope.launch { thumb.snapTo(cur) }
                        update()
                        ch.consume()
                    }
                    if (dir != null) sendCmd("STOP")
                    dir = null
                    scope.launch {
                        thumb.animateTo(Offset.Zero, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val arrowTint = { d: String -> if (dir == d) CtrlColors.Accent else CtrlColors.Muted }
        Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.ptz_up), Modifier.align(Alignment.TopCenter).padding(top = 6.dp), tint = arrowTint("MOVE_UP"))
        Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.ptz_down), Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), tint = arrowTint("MOVE_DOWN"))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.ptz_left), Modifier.align(Alignment.CenterStart).padding(start = 6.dp), tint = arrowTint("MOVE_LEFT"))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.ptz_right), Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), tint = arrowTint("MOVE_RIGHT"))
        Box(
            Modifier
                .offset { IntOffset(thumb.value.x.roundToInt(), thumb.value.y.roundToInt()) }
                .size(knob).clip(RoundedCornerShape(18.dp))
                .background(if (dir != null) CtrlColors.Accent else CtrlColors.Surface)
                .border(1.dp, if (dir != null) CtrlColors.Accent else CtrlColors.ControlOutline, RoundedCornerShape(18.dp))
        )
    }
}

/** Vertical pill with hold-to-zoom (+ / −), and optional focus pill. */
@Composable
fun PtzZoomPill(send: (String) -> Unit, zoom: Boolean, focus: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (zoom) Pill {
            HoldButton(Icons.Outlined.Add, stringResource(R.string.ptz_zoom_in), "ZOOM_IN", send)
            HoldButton(Icons.Outlined.Remove, stringResource(R.string.ptz_zoom_out), "ZOOM_OUT", send)
        }
        if (focus) Pill {
            HoldButton(Icons.Outlined.CenterFocusStrong, stringResource(R.string.ptz_focus_near), "FOCUS_IN", send)
            HoldButton(Icons.Outlined.CenterFocusWeak, stringResource(R.string.ptz_focus_far), "FOCUS_OUT", send)
        }
    }
}

@Composable
private fun Pill(content: @Composable () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(CtrlColors.Raised)
            .border(1.dp, CtrlColors.ControlOutline, RoundedCornerShape(18.dp)).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun HoldButton(icon: ImageVector, label: String, command: String, send: (String) -> Unit, size: Dp = 48.dp) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp))
            .background(if (pressed) CtrlColors.Accent else CtrlColors.Surface)
            .pointerInput(command) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    send(command)
                    try { tryAwaitRelease() } finally {
                        send("STOP")
                        pressed = false
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(20.dp), tint = if (pressed) CtrlColors.OnAccent else CtrlColors.Text)
    }
}
