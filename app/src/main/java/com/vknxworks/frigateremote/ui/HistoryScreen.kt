package com.vknxworks.frigateremote.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vknxworks.frigateremote.App
import com.vknxworks.frigateremote.data.CameraInfo
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(camera: String, info: CameraInfo?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = App.api
    val settings = App.settings
    val cs = MaterialTheme.colorScheme
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    val tl = remember {
        TimelineState(nowSec() - 60, initialSpp = (settings.timelineMinutes * 60.0 / widthPx).coerceIn(MIN_SPP, MAX_SPP))
    }
    val playback = remember(camera) { Playback(ctx, camera, scope) }
    DisposableEffect(playback) { onDispose { playback.release() } }

    // Start a minute back from now (recordings are written in short segments, so "now" isn't on disk yet).
    LaunchedEffect(playback) { playback.seek(nowSec() - 60, play = true) }
    ImmersiveMode(landscape)

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

    // ---- keep the playhead on the player's position ----
    LaunchedEffect(playback) {
        while (true) {
            if (!tl.interacting && !playback.loading && !playback.noRecording)
                playback.currentTime()?.let { tl.center = it }
            delay(80)
        }
    }
    playback.onWindowEnded = { end ->
        if (end < nowSec() - 30) playback.seek(end + 0.05, play = true) else playback.pause()
    }

    var wasPlaying by remember { mutableStateOf(false) }
    var previewTime by remember { mutableStateOf<Double?>(null) }
    var lastFastSeek by remember { mutableLongStateOf(0L) }

    fun seekTo(t: Double, play: Boolean) {
        val target = t.coerceAtMost(nowSec() - 15)
        tl.center = target
        playback.seek(target, play)
    }

    val onScrubStart: () -> Unit = {
        wasPlaying = playback.player.playWhenReady
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
        val base = playback.currentTime() ?: tl.center
        seekTo(base + delta, play = playback.isPlaying)
    }

    fun jumpEvent(next: Boolean) {
        val c = tl.center
        val pre = settings.eventPreroll
        val r = if (next) reviews.firstOrNull { it.start - pre > c + 1 } else reviews.lastOrNull { it.start - pre < c - 2 }
        r?.let { seekTo(it.start - pre, play = true) }
    }

    var showEvents by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    val video: @Composable (Modifier) -> Unit = { m ->
        Box(m.background(Color.Black)) {
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
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), strokeWidth = 3.dp, color = Color.White)
            if (playback.noRecording && previewTime == null)
                OverlayChip("No recording at this time", null, Modifier.align(Alignment.Center))
            playback.error?.let {
                OverlayChip(it, Status.alert, Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            OverlayChip(
                if (playback.speed == 1f) "PLAYBACK" else "PLAYBACK · ${speedLabel(playback.speed)}",
                cs.primary, Modifier.align(Alignment.TopStart).padding(10.dp),
            )
        }
    }

    val controls: @Composable () -> Unit = {
        TimeReadout(
            t = tl.center, spp = tl.spp, widthPx = widthPx,
            onZoomIn = { tl.spp = (tl.spp / 2).coerceIn(MIN_SPP, MAX_SPP) },
            onZoomOut = { tl.spp = (tl.spp * 2).coerceIn(MIN_SPP, MAX_SPP) },
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = cs.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Timeline(
                state = tl, segments = segments, reviews = reviews, nowProvider = ::nowSec,
                onScrubStart = onScrubStart, onScrub = onScrub, onScrubEnd = onScrubEnd,
                hapticTicks = settings.timelineHaptics,
                modifier = Modifier.fillMaxWidth().height(92.dp),
            )
        }
        Transport(
            playing = playback.isPlaying,
            speed = playback.speed,
            onPlay = { playback.togglePlay() },
            onStep = ::step,
            onEvent = ::jumpEvent,
            onSpeed = { playback.cycleSpeed() },
        )
    }

    if (landscape) {
        Column(Modifier.fillMaxSize().background(cs.background)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                video(Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    VideoIconButton(Icons.Outlined.CalendarMonth, "Pick date") { showDate = true }
                    VideoIconButton(Icons.AutoMirrored.Outlined.List, "Events") { showEvents = true }
                    VideoIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back to live", onClick = onBack)
                }
            }
            Column(Modifier.navigationBarsPadding()) { controls() }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(info?.displayName ?: camera, fontWeight = FontWeight.SemiBold)
                            Text("History", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to live") } },
                    actions = {
                        IconButton({ showDate = true }) { Icon(Icons.Outlined.CalendarMonth, "Pick date") }
                        IconButton({ showEvents = true }) {
                            BadgedBox(badge = { if (reviews.isNotEmpty()) Badge { Text("${reviews.size.coerceAtMost(99)}") } }) {
                                Icon(Icons.AutoMirrored.Outlined.List, "Events")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                )
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                video(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.large)
                )
                Spacer(Modifier.height(4.dp))
                controls()
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) { Legend() }
            }
        }
    }

    if (showEvents) {
        ModalBottomSheet(onDismissRequest = { showEvents = false }) {
            EventsList(reviews.asReversed()) { r ->
                showEvents = false
                seekTo(r.start - settings.eventPreroll, play = true)
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
                        tl.spp = (6 * 3600.0 / widthPx).coerceIn(MIN_SPP, MAX_SPP)
                        seekTo(target, play = false)
                    }
                }) { Text("Go") }
            },
            dismissButton = { TextButton({ showDate = false }) { Text("Cancel") } },
        ) { DatePicker(dp) }
    }
}

private fun speedLabel(s: Float) = if (s < 1f) "${s}×" else "${s.toInt()}×"

@Composable
private fun TimeReadout(t: Double, spp: Double, widthPx: Float, onZoomIn: () -> Unit, onZoomOut: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fmtDate = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }
    val fmtTime = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val d = Date((t * 1000).toLong())
    val tenths = ((t * 10).toLong() % 10)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(fmtDate.format(d), color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmtTime.format(d), fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Text(".$tenths", fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        Surface(shape = RoundedCornerShape(50), color = cs.surfaceContainerHigh) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onZoomOut) { Icon(Icons.Outlined.ZoomOut, "Zoom timeline out") }
                Text(zoomLabel(spp * widthPx), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                IconButton(onZoomIn) { Icon(Icons.Outlined.ZoomIn, "Zoom timeline in") }
            }
        }
    }
}

/** Human label for how much time the timeline currently spans. */
private fun zoomLabel(spanSec: Double): String = when {
    spanSec < 120 -> "${spanSec.toInt()} s"
    spanSec < 7200 -> "${(spanSec / 60).toInt()} min"
    else -> "${"%.1f".format(Locale.US, spanSec / 3600)} h"
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton({ onEvent(false) }) { Icon(Icons.Outlined.SkipPrevious, "Previous event") }
            StepButton("−10s") { onStep(-10.0) }
            StepButton("−1s") { onStep(-1.0) }
            FilledIconButton(onPlay, Modifier.size(60.dp)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play",
                    modifier = Modifier.size(32.dp))
            }
            StepButton("+1s") { onStep(1.0) }
            StepButton("+10s") { onStep(10.0) }
            IconButton({ onEvent(true) }) { Icon(Icons.Outlined.SkipNext, "Next event") }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−30s", outlined = true) { onStep(-30.0) }
            StepButton("−0.1s", outlined = true) { onStep(-0.1) }
            FilledTonalButton(onSpeed, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text(speedLabel(speed), fontWeight = FontWeight.Bold)
            }
            StepButton("+0.1s", outlined = true) { onStep(0.1) }
            StepButton("+30s", outlined = true) { onStep(30.0) }
        }
    }
}

@Composable
private fun StepButton(label: String, outlined: Boolean = false, onClick: () -> Unit) {
    val pad = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    if (outlined) {
        OutlinedButton(onClick, contentPadding = pad, modifier = Modifier.height(36.dp)) {
            Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    } else {
        FilledTonalButton(onClick, contentPadding = pad, modifier = Modifier.height(40.dp)) {
            Text(label, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Legend() {
    val cs = MaterialTheme.colorScheme
    val st = Status
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LegendDot(st.recording, "Recording")
        LegendDot(st.motion, "Motion")
        LegendDot(st.detection, "Detection")
        LegendDot(st.alert, "Alert")
    }
    Text(
        "Drag to scrub · pinch to zoom · tap to jump · fling for long jumps",
        color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun LegendDot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun EventsList(items: List<ReviewItem>, onPick: (ReviewItem) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val st = Status
    val fmt = remember { SimpleDateFormat("EEE HH:mm:ss", Locale.getDefault()) }
    Text("Events", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
    if (items.isEmpty()) {
        Text("No events in this part of the timeline", color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxWidth().height(540.dp)) {
        items(items, key = { it.id }) { r ->
            ListItem(
                modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { onPick(r) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small).background(cs.surfaceContainerHighest)) {
                        r.thumbPath?.let { RemoteImage(App.api.url(it), Modifier.fillMaxSize()) }
                    }
                },
                headlineContent = {
                    Text(
                        r.labels.joinToString(", ").ifBlank { r.severity }.replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Medium,
                    )
                },
                supportingContent = {
                    val dur = r.end?.let { "${(it - r.start).toInt()} s" } ?: "ongoing"
                    Text("${fmt.format(Date((r.start * 1000).toLong()))} · $dur")
                },
                trailingContent = {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (r.isAlert) st.alert else st.detection))
                },
            )
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
