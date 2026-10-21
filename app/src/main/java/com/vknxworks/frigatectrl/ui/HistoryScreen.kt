package com.vknxworks.frigatectrl.ui

import android.content.res.Configuration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ContentCut
import kotlinx.coroutines.launch
import com.vknxworks.frigatectrl.data.Clips
import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.data.CameraInfo
import com.vknxworks.frigatectrl.data.RecordingSegment
import com.vknxworks.frigatectrl.data.ReviewItem
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.min
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.StringRes
import com.vknxworks.frigatectrl.R

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
    // Pause recordings when the app goes to the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback.pause() }

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
        r?.let {
            seekTo(it.start - pre, play = true)
        }
    }

    var showEvents by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    val zoom = remember(camera) { ZoomState() }

    // ---- clip selection: start is marked, end follows the playhead ----
    var clipStart by remember { mutableStateOf<Double?>(null) }
    var clipBusy by remember { mutableStateOf(false) }
    fun runClip(start: Double, end: Double, share: Boolean) {
        if (clipBusy) return
        val a = minOf(start, end); val b = maxOf(start, end)
        if (b - a < 1) { Toast.makeText(ctx, ctx.getString(R.string.clip_move_playhead), Toast.LENGTH_SHORT).show(); return }
        if (b - a > Clips.MAX_SECONDS) { Toast.makeText(ctx, ctx.getString(R.string.clip_too_long), Toast.LENGTH_SHORT).show(); return }
        clipBusy = true
        Toast.makeText(ctx, ctx.getString(R.string.clip_preparing), Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                if (share) Clips.share(ctx, camera, a, b)
                else {
                    val name = Clips.save(ctx, camera, a, b)
                    Toast.makeText(ctx, ctx.getString(R.string.clip_saved, name), Toast.LENGTH_LONG).show()
                }
                clipStart = null
            } catch (e: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.clip_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
            } finally { clipBusy = false }
        }
    }

    // ---- reviewed state ----
    fun markReviewed(ids: List<String>, reviewed: Boolean) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        reviews = reviews.map { if (it.id in set) it.copy(reviewed = reviewed) else it }
        scope.launch {
            try { api.setReviewed(ids, reviewed) }
            catch (e: Exception) { Toast.makeText(ctx, ctx.getString(R.string.update_failed, e.message.orEmpty()), Toast.LENGTH_SHORT).show() }
        }
    }

    val video: @Composable (Modifier) -> Unit = { m ->
        Box(m.background(Color.Black).zoomable(zoom)) {
            PlayerSurface(playback.player, zoom)
            previewTime?.let { ScrubPreview(api.snapshotAt(camera, it, 480), Modifier.fillMaxSize()) }
            if (playback.loading && previewTime == null)
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), strokeWidth = 3.dp, color = Color.White)
            if (playback.noRecording && previewTime == null)
                OverlayChip(stringResource(R.string.no_recording_here), null, Modifier.align(Alignment.Center))
            playback.error?.let {
                OverlayChip(it, Status.alert, Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            OverlayChip(
                if (playback.speed == 1f) stringResource(R.string.chip_playback) else stringResource(R.string.chip_playback_speed, speedLabel(playback.speed)),
                cs.primary, Modifier.align(Alignment.TopStart).padding(10.dp),
            )
            if (zoom.zoomed) OverlayChip(
                stringResource(R.string.zoom_reset, "%.1f".format(zoom.scale)), null,
                Modifier.align(Alignment.TopEnd).padding(10.dp), onClick = { zoom.reset() },
            )
        }
    }

    val controls: @Composable () -> Unit = {
        clipStart?.let { start ->
            ClipBar(
                start = start, end = tl.center, busy = clipBusy,
                onCancel = { clipStart = null },
                onSave = { runClip(start, tl.center, share = false) },
                onShare = { runClip(start, tl.center, share = true) },
            )
        }
        TimeReadout(
            t = tl.center, spp = tl.spp, widthPx = widthPx,
            onZoomIn = { tl.spp = (tl.spp / 2).coerceIn(MIN_SPP, MAX_SPP) },
            onZoomOut = { tl.spp = (tl.spp * 2).coerceIn(MIN_SPP, MAX_SPP) },
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Timeline(
                state = tl, segments = segments, reviews = reviews, nowProvider = ::nowSec,
                onScrubStart = onScrubStart, onScrub = onScrub, onScrubEnd = onScrubEnd,
                hapticTicks = settings.timelineHaptics,
                selection = clipStart?.let { it to tl.center },
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
                    VideoIconButton(Icons.Outlined.ContentCut, stringResource(R.string.make_clip)) { clipStart = if (clipStart == null) tl.center else null }
                    VideoIconButton(Icons.Outlined.CalendarMonth, stringResource(R.string.pick_date)) { showDate = true }
                    VideoIconButton(Icons.AutoMirrored.Outlined.List, stringResource(R.string.events)) { showEvents = true }
                    VideoIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_live), onClick = onBack)
                }
            }
            Column(Modifier.navigationBarsPadding()) { controls() }
        }
    } else {
        Column(Modifier.fillMaxSize().background(cs.background)) {
            // ---- compact header ----
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_live)) }
                Column(Modifier.weight(1f)) {
                    Text(info?.displayName ?: camera, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(stringResource(R.string.history), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                IconButton({ clipStart = if (clipStart == null) tl.center else null }) {
                    Icon(Icons.Outlined.ContentCut, stringResource(R.string.make_clip), tint = if (clipStart != null) cs.primary else cs.onSurfaceVariant)
                }
                FilledTonalIconButton({ showDate = true }) { Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.pick_date)) }
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton({ showEvents = true }) {
                    BadgedBox(badge = {
                        val unseen = reviews.count { !it.reviewed }
                        if (unseen > 0) Badge { Text("${unseen.coerceAtMost(99)}") }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.List, stringResource(R.string.events))
                    }
                }
            }
            video(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(28.dp))
            )
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) { Legend() }

            // ---- bottom dock: readout, timeline, transport ----
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = cs.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.navigationBarsPadding().padding(top = 10.dp, bottom = 4.dp)) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50)).background(cs.outlineVariant)
                    )
                    controls()
                }
            }
        }
    }

    if (showEvents) {
        ModalBottomSheet(onDismissRequest = { showEvents = false }) {
            EventsList(
                items = reviews.asReversed(),
                onPick = { r ->
                    showEvents = false
                    if (!r.reviewed) markReviewed(listOf(r.id), true)
                    seekTo(r.start - settings.eventPreroll, play = true)
                },
                onReviewed = { r, v -> markReviewed(listOf(r.id), v) },
                onMarkAll = { markReviewed(reviews.filter { !it.reviewed }.map { it.id }, true) },
                onClip = { r, share ->
                    runClip(r.start - settings.eventPreroll, (r.end ?: nowSec()) + 2, share)
                },
            )
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
                }) { Text(stringResource(R.string.go)) }
            },
            dismissButton = { TextButton({ showDate = false }) { Text(stringResource(R.string.cancel)) } },
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
                IconButton(onZoomOut) { Icon(Icons.Outlined.ZoomOut, stringResource(R.string.zoom_timeline_out)) }
                Text(zoomLabel(spp * widthPx), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                IconButton(onZoomIn) { Icon(Icons.Outlined.ZoomIn, stringResource(R.string.zoom_timeline_in)) }
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
            IconButton({ onEvent(false) }) { Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.previous_event)) }
            StepButton("−10s") { onStep(-10.0) }
            StepButton("−1s") { onStep(-1.0) }
            FilledIconButton(onPlay, Modifier.size(60.dp)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play),
                    modifier = Modifier.size(32.dp))
            }
            StepButton("+1s") { onStep(1.0) }
            StepButton("+10s") { onStep(10.0) }
            IconButton({ onEvent(true) }) { Icon(Icons.Outlined.SkipNext, stringResource(R.string.next_event)) }
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
        LegendDot(st.recording, stringResource(R.string.toggle_record))
        LegendDot(st.motion, stringResource(R.string.legend_motion))
        LegendDot(st.detection, stringResource(R.string.legend_detection))
        LegendDot(st.alert, stringResource(R.string.legend_alert))
    }
    Text(
        stringResource(R.string.timeline_help),
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

private enum class EventFilter(@StringRes val label: Int) { All(R.string.filter_all), Unreviewed(R.string.filter_unreviewed), Alerts(R.string.filter_alerts) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventsList(
    items: List<ReviewItem>,
    onPick: (ReviewItem) -> Unit,
    onReviewed: (ReviewItem, Boolean) -> Unit,
    onMarkAll: () -> Unit,
    onClip: (ReviewItem, share: Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val st = Status
    val fmt = remember { SimpleDateFormat("EEE HH:mm:ss", Locale.getDefault()) }
    var filter by rememberSaveable { mutableStateOf(EventFilter.All) }
    val shown = items.filter {
        when (filter) {
            EventFilter.All -> true
            EventFilter.Unreviewed -> !it.reviewed
            EventFilter.Alerts -> it.isAlert
        }
    }
    val unseen = items.count { !it.reviewed }

    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.events), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (unseen > 0) pluralStringResource(R.plurals.events_unreviewed, unseen, unseen) else stringResource(R.string.events_all_caught_up),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
        if (unseen > 0) TextButton(onMarkAll) {
            Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.mark_all))
        }
    }
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventFilter.entries.forEach { f ->
            FilterChip(filter == f, { filter = f }, label = { Text(stringResource(f.label)) })
        }
    }
    if (shown.isEmpty()) {
        Text(
            stringResource(if (filter == EventFilter.Unreviewed) R.string.events_none_unreviewed else R.string.events_none),
            color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxWidth().height(540.dp)) {
        items(shown, key = { it.id }) { r ->
            val dismiss = rememberSwipeToDismissBoxState(
                confirmValueChange = { v ->
                    if (v == SwipeToDismissBoxValue.StartToEnd) onReviewed(r, !r.reviewed)
                    false // snap back; the row just changes style
                },
            )
            SwipeToDismissBox(
                state = dismiss,
                enableDismissFromEndToStart = false,
                backgroundContent = {
                    Row(
                        Modifier.fillMaxSize().background(cs.primaryContainer).padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (r.reviewed) Icons.Outlined.MarkEmailUnread else Icons.Outlined.DoneAll, null,
                            tint = cs.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(if (r.reviewed) R.string.mark_unreviewed else R.string.mark_reviewed), color = cs.onPrimaryContainer)
                    }
                },
                modifier = Modifier.animateItem(),
            ) {
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    modifier = Modifier.clickable { onPick(r) },
                    colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                    leadingContent = {
                        Box(
                            Modifier.width(112.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)
                                .background(cs.surfaceContainerHighest)
                        ) {
                            r.thumbPath?.let {
                                RemoteImage(
                                    App.api.url(it),
                                    Modifier.fillMaxSize().graphicsLayer { alpha = if (r.reviewed) 0.6f else 1f },
                                )
                            }
                        }
                    },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (r.isAlert) st.alert else st.detection))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                r.labels.joinToString(", ").ifBlank { r.severity }.replaceFirstChar { it.uppercase() },
                                fontWeight = if (r.reviewed) FontWeight.Normal else FontWeight.Bold,
                                color = if (r.reviewed) cs.onSurfaceVariant else cs.onSurface,
                            )
                        }
                    },
                    supportingContent = {
                        val dur = r.end?.let { "${(it - r.start).toInt()} s" } ?: stringResource(R.string.event_ongoing)
                        Text("${fmt.format(Date((r.start * 1000).toLong()))} · $dur")
                    },
                    trailingContent = {
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (r.reviewed) R.string.mark_unreviewed else R.string.mark_reviewed)) },
                                    leadingIcon = { Icon(if (r.reviewed) Icons.Outlined.MarkEmailUnread else Icons.Outlined.DoneAll, null) },
                                    onClick = { menu = false; onReviewed(r, !r.reviewed) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.clip_save)) },
                                    leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                    onClick = { menu = false; onClip(r, false) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.clip_share)) },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                    onClick = { menu = false; onClip(r, true) },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Shown above the timeline while a clip is being selected. */
@Composable
private fun ClipBar(
    start: Double,
    end: Double,
    busy: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val a = minOf(start, end); val b = maxOf(start, end)
    val len = (b - a).toInt()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cs.primaryContainer,
        contentColor = cs.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.clip_length, len / 60, len % 60), fontWeight = FontWeight.Bold)
                Text(
                    if (len < 1) stringResource(R.string.clip_scrub_to_end) else "${fmt.format(Date((a * 1000).toLong()))} → ${fmt.format(Date((b * 1000).toLong()))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 8.dp), strokeWidth = 2.dp)
            else {
                IconButton(onSave) { Icon(Icons.Outlined.Download, stringResource(R.string.save_to_gallery)) }
                IconButton(onShare) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
            }
            IconButton(onCancel) { Icon(Icons.Outlined.Close, stringResource(R.string.clip_cancel)) }
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
