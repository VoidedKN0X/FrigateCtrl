package com.vknxworks.frigateremote.ui

import android.content.res.Configuration
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
import com.vknxworks.frigateremote.App
import com.vknxworks.frigateremote.data.CameraInfo
import com.vknxworks.frigateremote.data.PtzInfo
import com.vknxworks.frigateremote.data.PtzSocket

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

@OptIn(ExperimentalMaterial3Api::class)
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

    // Last 24 h activity for the history card.
    var counts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(camera) {
        counts = try {
            val r = api.reviews(camera, nowSec() - 86400, nowSec())
            r.count { it.isAlert } to r.count { !it.isAlert }
        } catch (_: Exception) { null }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(info?.displayName ?: camera, fontWeight = FontWeight.SemiBold)
                        Text("Live", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (streams.size > 1) Box {
                        IconButton({ streamMenu = true }) { Icon(Icons.Outlined.HighQuality, "Stream") }
                        DropdownMenu(streamMenu, { streamMenu = false }) {
                            streams.forEachIndexed { i, s ->
                                DropdownMenuItem(
                                    text = { Text(s.label, fontWeight = if (i == streamIdx) FontWeight.Bold else null) },
                                    onClick = { streamIdx = i; streamMenu = false },
                                )
                            }
                        }
                    }
                    IconButton({ muted = !muted }) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                            if (muted) "Unmute" else "Mute",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large).background(Color.Black)
            ) {
                LiveView(camera, stream, muted, Modifier.fillMaxSize())
                OverlayChip("LIVE", Status.live, Modifier.align(Alignment.TopStart).padding(10.dp), pulse = true)
                VideoIconButton(
                    Icons.Outlined.Fullscreen, "Fullscreen", Modifier.align(Alignment.BottomEnd).padding(4.dp),
                ) { fullscreen = true }
            }

            HistoryCard(counts, onHistory)

            val p = ptz
            if (p != null && (p.panTilt || p.zoom || p.focus || p.presets.isNotEmpty())) {
                PtzPanel(p, send = { ptzSocket.send(camera, it) })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryCard(counts: Pair<Int, Int>?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.History, null, tint = cs.onPrimary) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("View history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val sub = counts?.let { (a, d) -> "Last 24 h · $a alerts · $d detections" }
                    ?: "Recordings, events and timeline"
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

/** Round translucent button for use on top of video. */
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
