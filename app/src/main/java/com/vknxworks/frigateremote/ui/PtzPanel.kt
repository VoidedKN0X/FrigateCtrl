package com.vknxworks.frigateremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.vknxworks.frigateremote.data.PtzInfo

/**
 * ONVIF PTZ controls (via Frigate). Hold a direction to move, release to stop — same as the web UI.
 */
@Composable
fun PtzPanel(info: PtzInfo, send: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (info.zoom) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HoldButton(Icons.Default.ZoomIn, "Zoom in", "ZOOM_IN", send)
                HoldButton(Icons.Default.ZoomOut, "Zoom out", "ZOOM_OUT", send)
            }
            if (info.panTilt) Box(
                Modifier.size(156.dp).clip(CircleShape).background(Palette.Surface),
                contentAlignment = Alignment.Center,
            ) {
                HoldButton(Icons.Default.KeyboardArrowUp, "Up", "MOVE_UP", send, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
                HoldButton(Icons.Default.KeyboardArrowDown, "Down", "MOVE_DOWN", send, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
                HoldButton(Icons.Default.KeyboardArrowLeft, "Left", "MOVE_LEFT", send, Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
                HoldButton(Icons.Default.KeyboardArrowRight, "Right", "MOVE_RIGHT", send, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
            }
            if (info.focus) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HoldButton(Icons.Default.CenterFocusStrong, "Focus in", "FOCUS_IN", send)
                HoldButton(Icons.Default.CenterFocusStrong, "Focus out", "FOCUS_OUT", send, alpha = 0.6f)
            }
        }
        if (info.presets.isNotEmpty()) {
            Text("Presets", color = Palette.Muted, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                info.presets.forEach { p ->
                    AssistChip(onClick = { send("preset_$p") }, label = { Text(p) })
                }
            }
        }
    }
}

@Composable
private fun HoldButton(
    icon: ImageVector,
    label: String,
    command: String,
    send: (String) -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
            .background(if (pressed) Palette.Accent.copy(alpha = 0.35f) else Palette.SurfaceHi)
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
        Icon(icon, label, tint = Palette.Text.copy(alpha = alpha))
    }
}
