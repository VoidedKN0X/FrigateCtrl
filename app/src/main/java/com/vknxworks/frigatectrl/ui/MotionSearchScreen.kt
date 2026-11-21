package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.MotionHit
import com.vknxworks.frigatectrl.data.MotionSearchStatus
import com.vknxworks.frigatectrl.notify.AlertNotifier
import com.vknxworks.frigatectrl.ui.components.CtrlButton
import com.vknxworks.frigatectrl.ui.components.CtrlChip
import com.vknxworks.frigatectrl.ui.components.CtrlProgress
import com.vknxworks.frigatectrl.ui.components.CtrlSubTopBar
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlShape
import com.vknxworks.frigatectrl.ui.theme.CtrlType
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val MOTION_RANGES = listOf(1, 6, 24, 72)

/**
 * Motion search (Frigate 0.18 and newer): drag a rectangle over the part of the picture you care about,
 * pick how far back to look, and Frigate searches the recordings for moments where that part changed.
 * Tapping a result opens History there. Only reachable when the server is 0.18 or newer.
 */
@Composable
fun MotionSearchScreen(camera: String, onBack: () -> Unit, onOpenAt: (Double) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var left by remember { mutableFloatStateOf(0.25f) }
    var top by remember { mutableFloatStateOf(0.25f) }
    var right by remember { mutableFloatStateOf(0.75f) }
    var bottom by remember { mutableFloatStateOf(0.75f) }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    var hours by remember { mutableIntStateOf(6) }
    var status by remember { mutableStateOf<MotionSearchStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var jobId by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val running = job?.isActive == true

    fun start() {
        if (running) return
        error = null
        status = null
        job = scope.launch {
            var id: String? = null
            try {
                val now = nowSec()
                id = App.api.startMotionSearch(camera, now - hours * 3600.0, now, left, top, right, bottom)
                jobId = id
                while (true) {
                    val s = App.api.motionSearchStatus(camera, id)
                    if (s != null) status = s
                    if (s != null && !s.running) {
                        if (s.status == "failed") error = s.message ?: ctx.getString(R.string.motion_search_failed)
                        break
                    }
                    delay(1500)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Leaving the screen or pressing Stop: tell Frigate to stop working on it.
                id?.let { withContext(NonCancellable) { App.api.cancelMotionSearch(camera, it) } }
                throw e
            } catch (e: Exception) {
                error = e.message ?: ctx.getString(R.string.motion_search_failed)
            } finally {
                jobId = null
                job = null
            }
        }
    }
    // Leaving the screen stops a search that is still running.
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val timeFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM) }
    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlSubTopBar(
            title = AlertNotifier.cameraLabel(camera),
            onBack = onBack,
            backIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            backDescription = stringResource(R.string.back),
            subtitle = {
                Text(stringResource(R.string.motion_search_title).uppercase(), style = CtrlType.Label, color = CtrlColors.Muted, modifier = Modifier.padding(top = 2.dp))
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navBottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "picture") {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(aspect).clip(CtrlShape.Tile).background(CtrlColors.VideoBackdrop)
                        .pointerInput(running) {
                            if (running) return@pointerInput
                            detectDragGestures(
                                onDragStart = { p ->
                                    left = (p.x / size.width).coerceIn(0f, 1f); right = left
                                    top = (p.y / size.height).coerceIn(0f, 1f); bottom = top
                                },
                                onDrag = { change, _ ->
                                    right = (change.position.x / size.width).coerceIn(0f, 1f)
                                    bottom = (change.position.y / size.height).coerceIn(0f, 1f)
                                },
                            )
                        },
                ) {
                    // The picture fills the box exactly (the box takes the picture's shape), so a spot in
                    // the box is the same spot in the camera's picture.
                    RemoteImage(
                        App.api.latestJpg(camera, 480), Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
                        onBitmap = { b -> if (b.width > 0 && b.height > 0) aspect = b.width.toFloat() / b.height },
                    )
                    val accent = CtrlColors.Accent
                    Canvas(Modifier.fillMaxSize()) {
                        val l = minOf(left, right) * size.width
                        val t = minOf(top, bottom) * size.height
                        val w = kotlin.math.abs(right - left) * size.width
                        val h = kotlin.math.abs(bottom - top) * size.height
                        drawRect(accent.copy(alpha = 0.18f), Offset(l, t), Size(w, h))
                        drawRect(accent, Offset(l, t), Size(w, h), style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
            item(key = "hint") {
                Text(stringResource(R.string.motion_search_hint), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            }
            item(key = "ranges") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MOTION_RANGES.forEach { h ->
                        CtrlChip(
                            stringResource(
                                when (h) {
                                    1 -> R.string.motion_range_1h
                                    6 -> R.string.motion_range_6h
                                    24 -> R.string.motion_range_24h
                                    else -> R.string.motion_range_3d
                                },
                            ),
                            hours == h, { if (!running) hours = h },
                        )
                    }
                }
            }
            item(key = "start") {
                val tooSmall = kotlin.math.abs(right - left) < 0.02f || kotlin.math.abs(bottom - top) < 0.02f
                if (running) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val p = status?.progress
                        if (p != null) CtrlProgress(p.toFloat().coerceIn(0f, 1f), Modifier.fillMaxWidth())
                        Text(
                            if (p != null) stringResource(R.string.motion_search_running_percent, (p * 100).toInt().coerceIn(0, 100))
                            else stringResource(R.string.motion_search_running),
                            style = CtrlType.Mono, color = CtrlColors.Muted,
                        )
                        CtrlButton(
                            stringResource(R.string.motion_search_stop), { job?.cancel() }, Modifier.fillMaxWidth(),
                            containerColor = CtrlColors.Surface, contentColor = CtrlColors.Text,
                        )
                    }
                } else {
                    CtrlButton(stringResource(R.string.motion_search_start), { start() }, Modifier.fillMaxWidth(), enabled = !tooSmall)
                }
            }
            error?.let { e -> item(key = "error") { Text(e, color = CtrlColors.Error, style = MaterialTheme.typography.bodyMedium) } }
            val hits: List<MotionHit> = status?.hits.orEmpty()
            val s = status
            if (s != null && !s.running && s.status != "failed" && hits.isEmpty()) {
                item(key = "none") { Text(stringResource(R.string.motion_search_none), color = CtrlColors.Muted) }
            }
            items(hits, key = { it.time }) { hit ->
                Row(
                    Modifier.fillMaxWidth().clip(CtrlShape.Field).background(CtrlColors.Surface).clickable { onOpenAt(hit.time) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(timeFormat.format(Date((hit.time * 1000).toLong())), style = CtrlType.Mono, color = CtrlColors.Text)
                    Text(stringResource(R.string.motion_search_changed, hit.changePercent.toInt().coerceIn(0, 100)), style = CtrlType.Mono, color = CtrlColors.Muted)
                }
            }
        }
    }
}
