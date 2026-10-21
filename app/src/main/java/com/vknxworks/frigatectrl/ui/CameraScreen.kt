package com.vknxworks.frigatectrl.ui

import android.content.res.Configuration
import org.json.JSONObject
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.Switch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.layout.BoxScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import android.content.pm.PackageManager
import android.Manifest
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
import com.vknxworks.frigatectrl.data.FrigateSocket
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.vknxworks.frigatectrl.R

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
    val context = LocalContext.current
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
    var showToggles by remember { mutableStateOf(false) }
    val zoom = remember(camera) { ZoomState() }
    val stream = streams.getOrNull(streamIdx)?.streamName ?: camera

    // ---- Frigate websocket: PTZ + camera toggles ----
    val socket = remember { FrigateSocket(api) }
    val toggles = remember(camera) { mutableStateMapOf<String, Boolean>() }
    DisposableEffect(socket) {
        socket.onMessage = { topic, payload ->
            if (topic == "camera_activity") {
                runCatching {
                    val cfg = JSONObject(payload).optJSONObject(camera)?.optJSONObject("config")
                    if (cfg != null) CameraToggle.entries.forEach { t ->
                        if (cfg.has(t.configKey)) toggles[t.feature] = cfg.optBoolean(t.configKey)
                    }
                }
            } else if (topic.startsWith("$camera/") && topic.endsWith("/state")) {
                val feature = topic.removePrefix("$camera/").removeSuffix("/state")
                if (CameraToggle.entries.any { it.feature == feature }) toggles[feature] = payload.equals("ON", true)
            }
        }
        socket.connect()
        socket.requestState()
        onDispose { socket.onMessage = null; socket.close() }
    }
    val send: (String) -> Unit = { cmd -> socket.send(camera, cmd) }

    // Unreviewed alerts in the last 24 h, shown on the History button.
    var alerts by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(camera) {
        alerts = try {
            api.reviews(camera, nowSec() - 86400, nowSec()).count { it.isAlert && !it.reviewed }
        } catch (_: Exception) { null }
    }

    // ---- two-way talk ----
    var canTalk by remember(stream) { mutableStateOf(false) }
    LaunchedEffect(stream) { canTalk = api.supportsTwoWayTalk(stream) }
    val activity = context.findActivity()
    val talk = remember(stream, activity) { activity?.let { TalkSession(it, stream) } }
    DisposableEffect(talk) { onDispose { talk?.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { talk?.stop() }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) talk?.start()
        else Toast.makeText(context, context.getString(R.string.talk_mic_needed), Toast.LENGTH_SHORT).show()
    }
    val toggleTalk: () -> Unit = {
        when (talk?.state) {
            TalkState.Talking, TalkState.Connecting -> talk?.stop()
            else -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    talk?.start()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    LaunchedEffect(talk?.error) {
        talk?.error?.let { Toast.makeText(context, context.getString(R.string.talk_error, it), Toast.LENGTH_LONG).show() }
    }

    // ---- picture-in-picture ----
    // Auto picture-in-picture respects "Hide in screenshots" (the live view would float over other apps), and
    // is off while the app lock locks as soon as you leave (the floating window would only show the lock).
    val autoPip = settings.autoPip && !App.lock.hideScreenshots && !(App.vault.enabled && App.lock.lockAfterSeconds == 0)
    val enterPip = rememberPipController(autoPip)
    val inPip = rememberIsInPip()
    if (inPip) {
        // Only the picture in the floating window.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            LiveView(camera, stream, muted, Modifier.fillMaxSize(), zoom = zoom, showNotes = false)
        }
        return
    }

    val immersive = fullscreen || landscape
    ImmersiveMode(immersive)
    if (fullscreen) BackHandler { fullscreen = false }

    val videoOverlay: @Composable BoxScope.() -> Unit = {
        if (zoom.zoomed) OverlayChip(
            stringResource(R.string.zoom_reset, "%.1f".format(zoom.scale)), null,
            Modifier.align(Alignment.TopEnd).padding(12.dp), onClick = { zoom.reset() },
        )
        if (talk?.state == TalkState.Talking) OverlayChip(
            stringResource(R.string.chip_talking), Status.alert, Modifier.align(Alignment.TopCenter).padding(12.dp), pulse = true,
        )
    }

    if (immersive) {
        Box(Modifier.fillMaxSize().background(Color.Black).zoomable(zoom)) {
            LiveView(camera, stream, muted, Modifier.fillMaxSize(), zoom = zoom)
            OverlayChip(stringResource(R.string.chip_live), Status.live, Modifier.align(Alignment.TopStart).padding(16.dp), pulse = true)
            videoOverlay()
            Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canTalk) VideoIconButton(
                    if (talk?.state == TalkState.Talking) Icons.Filled.Mic else Icons.Outlined.MicNone, stringResource(R.string.talk), onClick = toggleTalk,
                )
                VideoIconButton(
                    if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.audio),
                ) { muted = !muted }
                VideoIconButton(Icons.Outlined.PictureInPictureAlt, stringResource(R.string.picture_in_picture), onClick = enterPip)
                VideoIconButton(Icons.Outlined.History, stringResource(R.string.history), onClick = onHistory)
                VideoIconButton(Icons.Outlined.FullscreenExit, stringResource(R.string.exit_fullscreen)) {
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
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Column(Modifier.weight(1f)) {
                Text(info?.displayName ?: camera, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Status.live))
                    Text(streams.getOrNull(streamIdx)?.takeIf { streams.size > 1 }?.let { stringResource(R.string.live_stream, it.label) } ?: stringResource(R.string.live_plain),
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (streams.size > 1) Box {
                IconButton({ streamMenu = true }) { Icon(Icons.Outlined.HighQuality, stringResource(R.string.stream)) }
                DropdownMenu(streamMenu, { streamMenu = false }) {
                    streams.forEachIndexed { i, s ->
                        DropdownMenuItem(
                            text = { Text(s.label, fontWeight = if (i == streamIdx) FontWeight.Bold else null) },
                            onClick = { streamIdx = i; streamMenu = false },
                        )
                    }
                }
            }
            FilledTonalIconButton({ showToggles = true }) { Icon(Icons.Outlined.Tune, stringResource(R.string.camera_settings)) }
        }

        // ---- video ----
        Box(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(28.dp)).background(Color.Black).zoomable(zoom)
        ) {
            LiveView(camera, stream, muted, Modifier.fillMaxSize(), zoom = zoom)
            OverlayChip(stringResource(R.string.chip_live), Status.live, Modifier.align(Alignment.TopStart).padding(12.dp), pulse = true)
            videoOverlay()
            Row(Modifier.align(Alignment.BottomEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                VideoIconButton(
                    if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                    stringResource(if (muted) R.string.unmute else R.string.mute),
                ) { muted = !muted }
                VideoIconButton(Icons.Outlined.PictureInPictureAlt, stringResource(R.string.picture_in_picture), onClick = enterPip)
                VideoIconButton(Icons.Outlined.Fullscreen, stringResource(R.string.fullscreen)) { fullscreen = true }
            }
        }

        Spacer(Modifier.weight(1f))

        // ---- bottom control dock: presets above PTZ, History bottom-left, Talk bottom-right ----
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
                    Text(stringResource(R.string.ptz_presets), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
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
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HistoryButton(alerts, onHistory)
                    Spacer(Modifier.weight(1f))
                    if (canTalk && talk != null) TalkButton(talk.state, toggleTalk)
                }
            }
        }
    }

    if (showToggles) {
        ModalBottomSheet(onDismissRequest = { showToggles = false }) {
            CameraTogglesSheet(info, toggles) { t, on ->
                toggles[t.feature] = on
                socket.publish("$camera/${t.feature}/set", if (on) "ON" else "OFF")
            }
        }
    }
}

/** Settings Frigate lets you switch at runtime over its websocket. */
private enum class CameraToggle(
    val feature: String,
    val configKey: String,
    @StringRes val label: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
) {
    Enabled("enabled", "enabled", R.string.toggle_camera, R.string.toggle_camera_desc, Icons.Outlined.Videocam),
    Detect("detect", "detect", R.string.toggle_detect, R.string.toggle_detect_desc, Icons.Outlined.CenterFocusStrong),
    Record("recordings", "record", R.string.toggle_record, R.string.toggle_record_desc, Icons.Outlined.FiberManualRecord),
    Snapshots("snapshots", "snapshots", R.string.toggle_snapshots, R.string.toggle_snapshots_desc, Icons.Outlined.PhotoCamera),
    Audio("audio", "audio", R.string.toggle_audio, R.string.toggle_audio_desc, Icons.Outlined.GraphicEq),
    Notifications("notifications", "notifications", R.string.toggle_notifications, R.string.toggle_notifications_desc, Icons.Outlined.Notifications),
}

@Composable
private fun CameraTogglesSheet(
    info: CameraInfo?,
    state: Map<String, Boolean>,
    onToggle: (CameraToggle, Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val available = CameraToggle.entries.filter {
        when (it) {
            CameraToggle.Audio -> info?.hasAudioDetection == true
            CameraToggle.Notifications -> info?.hasNotifications == true
            else -> true
        }
    }
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.camera_settings_title, info?.displayName ?: stringResource(R.string.toggle_camera)), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            stringResource(R.string.camera_settings_note),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        available.forEach { t ->
            val on = state[t.feature]
            ListItem(
                leadingContent = { Icon(t.icon, null) },
                headlineContent = { Text(stringResource(t.label)) },
                supportingContent = { Text(stringResource(t.description)) },
                trailingContent = {
                    if (on == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Switch(on, { onToggle(t, it) })
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun TalkButton(state: TalkState, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val active = state == TalkState.Talking
    val connecting = state == TalkState.Connecting
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = source,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Status.alert else cs.primary,
            contentColor = if (active) Color.White else cs.onPrimary,
        ),
        modifier = Modifier.height(40.dp).pressScale(source),
    ) {
        if (connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.onPrimary)
        else Icon(if (active) Icons.Filled.Mic else Icons.Outlined.MicNone, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(when (state) { TalkState.Talking -> R.string.talk_stop; TalkState.Connecting -> R.string.connecting; else -> R.string.talk }),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
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
            Text(stringResource(R.string.history), style = MaterialTheme.typography.labelLarge)
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
