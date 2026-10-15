package com.vknxworks.frigatectrl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.data.CameraInfo
import com.vknxworks.frigatectrl.data.PtzInfo
import com.vknxworks.frigatectrl.data.PtzSocket

@Composable
fun CameraScreen(camera: String, onBack: () -> Unit) {
    val api = App.api
    var info by remember { mutableStateOf<CameraInfo?>(null) }
    var ptz by remember { mutableStateOf<PtzInfo?>(null) }
    LaunchedEffect(camera) {
        info = try { api.cameras().firstOrNull { it.name == camera } } catch (_: Exception) { null }
        if (info?.hasOnvif == true) ptz = api.ptzInfo(camera)
    }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn(App.settings.keepScreenOn)

    if (showHistory) {
        BackHandler { showHistory = false }
        HistoryScreen(camera, info, onBack = { showHistory = false })
    } else {
        LiveScreen(camera, info, ptz, onBack = onBack, onHistory = { showHistory = true })
    }
}

@Composable
private fun LiveScreen(
    camera: String,
    info: CameraInfo?,
    ptz: PtzInfo?,
    onBack: () -> Unit,
    onHistory: () -> Unit,
) {
    val api = App.api
    val settings = App.settings
    val cs = MaterialTheme.colorScheme
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val streams = info?.streams.orEmpty()
    var streamIdx by rememberSaveable(camera) { mutableIntStateOf(-1) }
    LaunchedEffect(streams.size) {
        if (streamIdx < 0 && streams.isNotEmpty())
            streamIdx = if (settings.preferSubStream && streams.size > 1) streams.lastIndex else 0
    }
    var muted by rememberSaveable { mutableStateOf(settings.startMuted) }
    var streamMenu by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    val ptzSocket = remember { PtzSocket(api) }
    DisposableEffect(ptzSocket) { onDispose { ptzSocket.close() } }
    LaunchedEffect(ptz) { if (ptz != null) ptzSocket.connect() }
    val send: (String) -> Unit = { ptzSocket.send(camera, it) }

    // Alerts in the last 24 h, shown on the History button.
    var alerts by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(camera) {
        alerts = try { api.reviews(camera, nowSec() - 86400, nowSec()).count { it.isAlert } } catch (_: Exception) { null }
    }

    val stream = streams.getOrNull(streamIdx)?.streamName ?: camera
    val immersive = fullscreen || landscape
    ImmersiveMode(immersive)
    if (fullscreen) BackHandler { fullscreen = false }

    if (immersive) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            LiveView(camera, stream, muted, Modifier.fillMaxSize())
            OverlayChip("LIVE", Status.live, Modifier.align(Alignment.TopStart).padding(16.dp), pulse = true)
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                VideoIconButton(
                    if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp, "Audio",
                ) { muted = !muted }
                VideoIconButton(Icons.Outlined.History, "History", onClick = onHistory)
                VideoIconButton(Icons.Outlined.FullscreenExit, "Exit fullscreen") {
                    if (fullscreen) fullscreen = false else onBack()
                }
            }
        }
        return
    }

    val p = ptz
    val hasPtz = p != null && (p.panTilt || p.zoom || p.focus)
    val hasPresets = p != null && p.presets.isNotEmpty()

    Column(Modifier.fillMaxSize().background(cs.background)) {
        // ---- compact header ----
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(info?.displayName ?: camera, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Status.live))
                    Text("  Live" + (streams.getOrNull(streamIdx)?.let { if (streams.size > 1) " · ${it.label}" else "" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
            if (streams.size > 1) Box {
                FilledTonalIconButton({ streamMenu = true }) { Icon(Icons.Outlined.HighQuality, "Stream") }
                DropdownMenu(streamMenu, { streamMenu = false }) {
                    streams.forEachIndexed { i, s ->
                        DropdownMenuItem(
                            text = { Text(s.label, fontWeight = if (i == streamIdx) FontWeight.Bold else null) },
                            onClick = { streamIdx = i; streamMenu = false },
                        )
                    }
                }
            }
        }

        // ---- video ----
        Box(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(28.dp)).background(Color.Black)
        ) {
            LiveView(camera, stream, muted, Modifier.fillMaxSize())
            OverlayChip("LIVE", Status.live, Modifier.align(Alignment.TopStart).padding(12.dp), pulse = true)
            Row(Modifier.align(Alignment.BottomEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                VideoIconButton(
                    if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                    if (muted) "Unmute" else "Mute",
                ) { muted = !muted }
                VideoIconButton(Icons.Outlined.Fullscreen, "Fullscreen") { fullscreen = true }
            }
        }

        Spacer(Modifier.weight(1f))

        // ---- bottom control dock: presets above PTZ, History bottom-left ----
        Surface(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.navigationBarsPadding().padding(top = 10.dp, bottom = 12.dp)) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50)).background(cs.outlineVariant)
                )
                if (hasPresets) {
                    Text("Presets", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp))
                    PtzPresets(p!!.presets, send)
                }
                if (p != null && hasPtz) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp).height(172.dp)) {
                        if (p.panTilt) PtzJoystick(send, Modifier.align(Alignment.Center))
                        if (p.zoom || p.focus)
                            PtzZoomPill(send, p.zoom, p.focus, Modifier.align(if (p.panTilt) Alignment.CenterEnd else Alignment.Center))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    HistoryButton(alerts, onHistory)
                }
            }
        }
    }
}

@Composable
private fun HistoryButton(alerts: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    BadgedBox(
        badge = { if ((alerts ?: 0) > 0) Badge(containerColor = Status.alert) { Text("${alerts!!.coerceAtMost(99)}") } },
        modifier = modifier,
    ) {
        FilledTonalButton(
            onClick = onClick,
            interactionSource = source,
            contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
            modifier = Modifier.height(40.dp).pressScale(source),
        ) {
            Icon(Icons.Outlined.History, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("History", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun VideoIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color(0x99000000), contentColor = Color.White,
        ),
    ) { Icon(icon, label) }
}
