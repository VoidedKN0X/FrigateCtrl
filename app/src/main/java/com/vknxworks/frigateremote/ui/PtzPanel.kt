package com.vknxworks.frigateremote.ui

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.ControlCamera
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vknxworks.frigateremote.data.PtzInfo

/**
 * ONVIF PTZ controls (via Frigate). Hold a direction to move, release to stop — same as the web UI.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PtzPanel(info: PtzInfo, send: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ControlCamera, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Text("  Pan · tilt · zoom", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (info.zoom) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoldButton(Icons.Outlined.ZoomIn, "Zoom in", "ZOOM_IN", send)
                    HoldButton(Icons.Outlined.ZoomOut, "Zoom out", "ZOOM_OUT", send)
                }
                if (info.panTilt) Box(
                    Modifier.size(176.dp).clip(CircleShape).background(cs.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(cs.surfaceContainerHighest))
                    HoldButton(Icons.Default.KeyboardArrowUp, "Up", "MOVE_UP", send, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
                    HoldButton(Icons.Default.KeyboardArrowDown, "Down", "MOVE_DOWN", send, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                    HoldButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", "MOVE_LEFT", send, Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
                    HoldButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", "MOVE_RIGHT", send, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
                }
                if (info.focus) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoldButton(Icons.Outlined.CenterFocusStrong, "Focus near", "FOCUS_IN", send)
                    HoldButton(Icons.Outlined.CenterFocusWeak, "Focus far", "FOCUS_OUT", send)
                }
            }
            if (info.presets.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Presets", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.presets.forEach { p ->
                        AssistChip(
                            onClick = { send("preset_$p") },
                            label = { Text(p) },
                            leadingIcon = { Icon(Icons.Outlined.Bookmark, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                    }
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
    size: Dp = 52.dp,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (pressed) cs.primary else cs.secondaryContainer)
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
        Icon(icon, label, tint = if (pressed) cs.onPrimary else cs.onSecondaryContainer)
    }
}
