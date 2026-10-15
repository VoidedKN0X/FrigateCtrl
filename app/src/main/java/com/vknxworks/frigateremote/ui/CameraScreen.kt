package com.vknxworks.frigateremote.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vknxworks.frigateremote.App
import com.vknxworks.frigateremote.data.CameraInfo
import com.vknxworks.frigateremote.data.PtzInfo
import com.vknxworks.frigateremote.data.PtzSocket
import com.vknxworks.frigateremote.data.RecordingSegment
import com.vknxworks.frigateremote.data.ReviewItem
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.min
import androidx.compose.foundation.Image

private fun nowSec() = System.currentTimeMillis() / 1000.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(camera: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = App.api
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var info by remember { mutableStateOf<CameraInfo?>(null) }
    var ptz by remember { mutableStateOf<PtzInfo?>(null) }
    LaunchedEffect(camera) {
        info = try { api.cameras().firstOrNull { it.name == camera } } catch (_: Exception) { null }
        if (info?.hasOnvif == true) ptz = api.ptzInfo(camera)
    }

    var streamIdx by rememberSaveable { mutableIntStateOf(0) }
    var live by rememberSaveable { mutableStateOf(true) }
    var muted by rememberSaveable { mutableStateOf(true) }

    val tl = remember { TimelineState(nowSec(), initialSpp = 1.0) }
    val playback = remember(camera) { Playback(ctx, camera, scope) }
    DisposableEffect(playback) { onDispose { playback.release() } }
    val ptzSocket = remember { PtzSocket(api) }
    DisposableEffect(ptzSocket) { onDispose { ptzSocket.close() } }
    LaunchedEffect(ptz) { if (ptz != null) ptzSocket.connect() }

    // ---- timeline data, loaded in 24 h chunks around the playhead ----
    var segments by remember { mutableStateOf(emptyList<RecordingSegment>()) }
    var reviews by remember { mutableStateOf(emptyList<ReviewItem>()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val bucket by remember { derivedStateOf { floor(tl.center / 21600.0).toLong() } }
    LaunchedEffect(bucket, refreshTick) {
        val a = bucket * 21600.0 - 43200
        val b = min(nowSec(), bucket * 21600.0 + 43200)
        try { segments = api.recordings(camera, a, b) } catch (_: Exception) {}
        try { reviews = api.reviews(camera, a, b) } catch (_: Exception) {}
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            if (nowSec() - tl.center < 43200) refreshTick++
        }
    }

    // ---- keep the playhead in sync ----
    LaunchedEffect(live) {
        if (live) {
            playback.pause()
            while (true) {
                if (!tl.interacting) tl.center = nowSec()
                delay(250)
            }
        } else {
            while (true) {
                if (!tl.interacting && !playback.loading && !playback.noRecording)
                    playback.currentTime()?.let { tl.center = it }
                delay(80)
            }
        }
    }
    playback.onWindowEnded = { end ->
        if (end < nowSec() - 15) playback.seek(end + 0.05, play = true) else live = true
    }

    // ---- scrubbing ----
    var wasPlaying by remember { mutableStateOf(false) }
    var previewTime by remember { mutableStateOf<Double?>(null) }
    var lastFastSeek by remember { mutableLongStateOf(0L) }

    fun seekTo(t: Double, play: Boolean) {
        val target = t.coerceAtMost(nowSec())
        if (target >= nowSec() - 3) { live = true; return }
        live = false
        tl.center = target
        playback.seek(target, play)
    }

    val onScrubStart: () -> Unit = {
        wasPlaying = live || playback.player.playWhenReady
        live = false
        playback.pause()
    }
    val onScrub: (Double) -> Unit = { t ->
        if (playback.inWindow(t)) {
            previewTime = null
            val ms = System.currentTimeMillis()
            if (ms - lastFastSeek > 90) {
                lastFastSeek = ms
                playback.seek(t, play = false, fast = true)
            }
        } else {
            previewTime = t
        }
    }
    val onScrubEnd: (Double) -> Unit = { t ->
        previewTime = null
        seekTo(t, wasPlaying)
    }

    fun step(delta: Double) {
        val base = if (live) nowSec() else (playback.currentTime() ?: tl.center)
        seekTo(base + delta, play = if (live) true else playback.isPlaying)
    }

    fun jumpEvent(next: Boolean) {
        val c = tl.center
        val r = if (next) reviews.firstOrNull { it.start > c + 1 } else reviews.lastOrNull { it.start < c - 3 }
        r?.let { seekTo(it.start - 2, play = true) }
    }

    var showEvents by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var streamMenu by remember { mutableStateOf(false) }

    // ================= layout =================
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).statusBarsPadding().navigationBarsPadding()
    ) {
        if (!landscape) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(info?.displayName ?: camera, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val streams = info?.streams.orEmpty()
                if (streams.size > 1) Box {
                    TextButton({ streamMenu = true }) { Text(streams.getOrNull(streamIdx)?.label ?: "Stream") }
                    DropdownMenu(streamMenu, { streamMenu = false }) {
                        streams.forEachIndexed { i, s ->
                            DropdownMenuItem(text = { Text(s.label) }, onClick = { streamIdx = i; streamMenu = false })
                        }
                    }
                }
                if (live) IconButton({ muted = !muted }) {
                    Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "Audio")
                }
                IconButton({ showDate = true }) { Icon(Icons.Default.CalendarMonth, "Pick date") }
                IconButton({ showEvents = true }) { Icon(Icons.Default.List, "Events") }
            }
        }

        // ---- video ----
        val videoMod = if (landscape) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        Box(videoMod.background(Color.Black)) {
            if (live) {
                val stream = info?.streams?.getOrNull(streamIdx)?.streamName ?: camera
                LiveView(camera, stream, muted, Modifier.fillMaxSize())
            } else {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            player = playback.player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                previewTime?.let { ScrubPreview(api.snapshotAt(camera, it, 480), Modifier.fillMaxSize()) }
                if (playback.loading && previewTime == null)
                    CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), strokeWidth = 3.dp)
                if (playback.noRecording && previewTime == null)
                    Text("No recording at this time", color = Palette.Muted, modifier = Modifier.align(Alignment.Center))
                playback.error?.let {
                    Text(it, color = Palette.Alert, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
            // mode chip
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50))
                    .background(Color(0xAA000000))
                    .clickable(enabled = !live) { live = true }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) Palette.Alert else Palette.Muted))
                Text(if (live) "LIVE" else "Go live", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp))
            }
            if (landscape) IconButton(onBack, Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        }

        // ---- time readout ----
        TimeReadout(
            t = tl.center, live = live && !tl.interacting, spp = tl.spp,
            onZoomIn = { tl.spp = (tl.spp / 2).coerceIn(MIN_SPP, MAX_SPP) },
            onZoomOut = { tl.spp = (tl.spp * 2).coerceIn(MIN_SPP, MAX_SPP) },
        )

        // ---- timeline ----
        Timeline(
            state = tl, segments = segments, reviews = reviews, nowProvider = ::nowSec,
            onScrubStart = onScrubStart, onScrub = onScrub, onScrubEnd = onScrubEnd,
            modifier = Modifier.fillMaxWidth().height(88.dp),
        )

        // ---- transport ----
        Transport(
            playing = !live && playback.isPlaying,
            speed = playback.speed,
            onPlay = { if (live) step(-10.0) else playback.togglePlay() },
            onStep = ::step,
            onEvent = ::jumpEvent,
            onSpeed = { playback.cycleSpeed() },
        )

        if (!landscape) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                val p = ptz
                if (live && p != null && (p.panTilt || p.zoom || p.presets.isNotEmpty())) {
                    PtzPanel(p, send = { ptzSocket.send(camera, it) }, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    Legend()
                }
            }
        }
    }

    // ---- sheets & dialogs ----
    if (showEvents) {
        ModalBottomSheet(onDismissRequest = { showEvents = false }, containerColor = Palette.Surface) {
            EventsList(reviews.asReversed()) { r ->
                showEvents = false
                seekTo(r.start - 2, play = true)
            }
        }
    }
    if (showDate) {
        val todayEnd = System.currentTimeMillis()
        val dp = rememberDatePickerState(
            initialSelectedDateMillis = todayEnd,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayEnd
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton({
                    showDate = false
                    dp.selectedDateMillis?.let { utc ->
                        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        val local = Calendar.getInstance().apply {
                            clear()
                            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
                        }
                        val target = min(local.timeInMillis / 1000.0, nowSec() - 1800)
                        tl.spp = 8.0
                        seekTo(target, play = false)
                    }
                }) { Text("Go") }
            },
            dismissButton = { TextButton({ showDate = false }) { Text("Cancel") } },
        ) { DatePicker(dp) }
    }
}

@Composable
private fun TimeReadout(t: Double, live: Boolean, spp: Double, onZoomIn: () -> Unit, onZoomOut: () -> Unit) {
    val fmtDate = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val fmtTime = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val d = Date((t * 1000).toLong())
    val tenths = ((t * 10).toLong() % 10)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(fmtDate.format(d), color = Palette.Muted, fontSize = 12.sp)
            Text(
                if (live) fmtTime.format(d) else "${fmtTime.format(d)}.$tenths",
                fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Medium,
            )
        }
        Text(zoomLabel(spp), color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
        IconButton(onZoomOut) { Icon(Icons.Default.ZoomOut, "Zoom timeline out") }
        IconButton(onZoomIn) { Icon(Icons.Default.ZoomIn, "Zoom timeline in") }
    }
}

/** Approximate span visible on a ~400dp-wide timeline. */
private fun zoomLabel(spp: Double): String {
    val span = spp * 1080
    return when {
        span < 120 -> "${span.toInt()} s"
        span < 7200 -> "${(span / 60).toInt()} min"
        else -> "${"%.1f".format(Locale.US, span / 3600)} h"
    }
}

@Composable
private fun Transport(
    playing: Boolean,
    speed: Float,
    onPlay: () -> Unit,
    onStep: (Double) -> Unit,
    onEvent: (Boolean) -> Unit,
    onSpeed: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onEvent(false) }) { Icon(Icons.Default.SkipPrevious, "Previous event") }
            StepButton("−10s") { onStep(-10.0) }
            StepButton("−1s") { onStep(-1.0) }
            FilledIconButton(onPlay, Modifier.size(52.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Accent)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/pause", tint = Color(0xFF00331F))
            }
            StepButton("+1s") { onStep(1.0) }
            StepButton("+10s") { onStep(10.0) }
            IconButton({ onEvent(true) }) { Icon(Icons.Default.SkipNext, "Next event") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onStep(-30.0) }) { Icon(Icons.Default.FastRewind, "Back 30 s") }
            StepButton("−0.1s") { onStep(-0.1) }
            TextButton(onSpeed) {
                Text(if (speed == 0.5f) "0.5×" else "${speed.toInt()}×", fontWeight = FontWeight.Bold)
            }
            StepButton("+0.1s") { onStep(0.1) }
            IconButton({ onStep(30.0) }) { Icon(Icons.Default.FastForward, "Forward 30 s") }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHi).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
}

@Composable
private fun ColumnScope.Legend() {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot(Palette.Accent, "Recording")
        LegendDot(Palette.Motion, "Motion")
        LegendDot(Palette.Detection, "Detection")
        LegendDot(Palette.Alert, "Alert")
    }
    Text(
        "Drag the timeline to scrub · pinch to zoom down to fractions of a second · tap to jump",
        color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun LegendDot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Text(label, color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun EventsList(items: List<ReviewItem>, onPick: (ReviewItem) -> Unit) {
    val fmt = remember { SimpleDateFormat("EEE HH:mm:ss", Locale.getDefault()) }
    if (items.isEmpty()) {
        Text("No events in this part of the timeline", color = Palette.Muted, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxWidth().height(520.dp)) {
        items(items, key = { it.id }) { r ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(r) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Palette.SurfaceHi)) {
                    r.thumbPath?.let { RemoteImage(App.api.url(it), Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (r.isAlert) Palette.Alert else Palette.Detection))
                        Text(
                            r.labels.joinToString(", ").ifBlank { r.severity }.replaceFirstChar { it.uppercase() },
                            fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    val dur = r.end?.let { "${(it - r.start).toInt()} s" } ?: "ongoing"
                    Text("${fmt.format(Date((r.start * 1000).toLong()))} · $dur", color = Palette.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Frame preview from Frigate's recording snapshot API while scrubbing outside the loaded window. */
@Composable
private fun ScrubPreview(url: String, modifier: Modifier) {
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        delay(120) // debounce while the finger is moving
        loadBitmap(url)?.let { bmp = it }
    }
    bmp?.let { Image(it, null, modifier.background(Color.Black), contentScale = ContentScale.Fit) }
}
