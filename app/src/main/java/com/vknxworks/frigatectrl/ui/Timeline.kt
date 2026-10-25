package com.vknxworks.frigatectrl.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.data.RecordingSegment
import com.vknxworks.frigatectrl.data.ReviewItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Seconds-per-pixel limits: ~20 s across a phone screen up to ~2 days. */
const val MIN_SPP = 0.02
const val MAX_SPP = 160.0

@Stable
class TimelineState(initialCenter: Double, initialSpp: Double = 2.0) {
    /** Time under the playhead, epoch seconds. */
    var center by mutableDoubleStateOf(initialCenter)
    /** Zoom: seconds per pixel. */
    var spp by mutableDoubleStateOf(initialSpp)
    var interacting by mutableStateOf(false)
        internal set
}

private data class TickStep(val major: Double, val minor: Double)

private val tickSteps = listOf(
    TickStep(1.0, 0.2), TickStep(2.0, 0.5), TickStep(5.0, 1.0), TickStep(10.0, 2.0),
    TickStep(15.0, 5.0), TickStep(30.0, 5.0), TickStep(60.0, 10.0), TickStep(120.0, 30.0),
    TickStep(300.0, 60.0), TickStep(600.0, 120.0), TickStep(900.0, 300.0), TickStep(1800.0, 300.0),
    TickStep(3600.0, 600.0), TickStep(7200.0, 1800.0), TickStep(10800.0, 3600.0),
    TickStep(21600.0, 3600.0), TickStep(43200.0, 10800.0), TickStep(86400.0, 21600.0),
)

/**
 * Horizontal scrubbing timeline. Fixed playhead in the middle; drag to move through time,
 * pinch to zoom (down to fractions of a second), fling for long jumps, tap to jump to a spot.
 */
@Composable
fun Timeline(
    state: TimelineState,
    segments: List<RecordingSegment>,
    reviews: List<ReviewItem>,
    nowProvider: () -> Double,
    onScrubStart: () -> Unit,
    onScrub: (Double) -> Unit,
    onScrubEnd: (Double) -> Unit,
    modifier: Modifier = Modifier,
    hapticTicks: Boolean = true,
    /** Highlighted range (e.g. a clip being selected), in epoch seconds. */
    selection: Pair<Double, Double>? = null,
) {
    val measurer = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    val st = Status
    val cBg = com.vknxworks.frigatectrl.ui.theme.CtrlColors.Background
    val cFuture = cs.surfaceContainerLowest.copy(alpha = 0.85f)
    val cMuted = cs.onSurfaceVariant
    val cLine = com.vknxworks.frigatectrl.ui.theme.CtrlColors.ControlOutline
    val cHead = cs.onSurface
    val cSel = cs.primary
    val hapticsOn by rememberUpdatedState(hapticTicks)
    val haptics = LocalHapticFeedback.current
    val start by rememberUpdatedState(onScrubStart)
    val scrub by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onScrubEnd)
    val now by rememberUpdatedState(nowProvider)

    val fmtSec = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val fmtMin = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val fmtDay = remember { SimpleDateFormat("EEE d", Locale.getDefault()) }
    val labelStyle = remember(cMuted) { TextStyle(color = cMuted, fontSize = 10.sp, fontFamily = com.vknxworks.frigatectrl.ui.theme.CtrlFonts.Mono) }
    val dayStyle = remember(cHead) { TextStyle(color = cHead, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.vknxworks.frigatectrl.ui.theme.CtrlFonts.Mono) }

    val gestures = Modifier.pointerInput(Unit) {
        val decay = splineBasedDecay<Float>(this)
        var flingJob: Job? = null
        var lastHapticIdx = Long.MIN_VALUE
        var lastHapticAt = 0L
        val minT = { now() - 31 * 86400.0 }
        fun clampT(t: Double) = t.coerceIn(minT(), now())
        fun moveTo(t: Double) {
            state.center = clampT(t)
            // A light tick each time a labelled mark passes under the playhead.
            val step = pickStep(state.spp).major
            val idx = floor(state.center / step).toLong()
            val nowMs = System.currentTimeMillis()
            if (hapticsOn && lastHapticIdx != Long.MIN_VALUE && idx != lastHapticIdx && nowMs - lastHapticAt > 45) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastHapticAt = nowMs
            }
            lastHapticIdx = idx
            scrub(state.center)
        }

        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                flingJob?.cancel()
                state.interacting = true
                lastHapticIdx = floor(state.center / pickStep(state.spp).major).toLong()
                start()
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var pinched = false
                var travelled = 0f
                while (true) {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size > 1) pinched = true
                    val zoom = ev.calculateZoom()
                    val pan = ev.calculatePan()
                    travelled += abs(pan.x)
                    if (zoom != 1f) state.spp = (state.spp / zoom).coerceIn(MIN_SPP, MAX_SPP)
                    if (pan.x != 0f) moveTo(state.center - pan.x * state.spp)
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    if (!pinched) pressed.first().let { tracker.addPosition(it.uptimeMillis, it.position) }
                }
                val vx = if (pinched) 0f else tracker.calculateVelocity().x
                when {
                    !pinched && travelled < viewConfiguration.touchSlop -> {
                        // Tap: jump to the tapped time.
                        moveTo(state.center + (down.position.x - size.width / 2f) * state.spp)
                        state.interacting = false
                        end(state.center)
                    }
                    abs(vx) > 400f -> {
                        flingJob = launch {
                            var last = 0f
                            try {
                                AnimationState(0f, vx).animateDecay(decay) {
                                    val d = value - last
                                    last = value
                                    moveTo(state.center - d * state.spp)
                                }
                                state.interacting = false
                                end(state.center)
                            } catch (e: CancellationException) {
                                throw e
                            }
                        }
                    }
                    else -> {
                        state.interacting = false
                        end(state.center)
                    }
                }
            }
        }
    }

    Canvas(modifier.then(gestures)) {
        val w = size.width
        val h = size.height
        val spp = state.spp
        val center = state.center
        val t0 = center - (w / 2.0) * spp
        val t1 = center + (w / 2.0) * spp
        fun x(t: Double) = ((t - t0) / spp).toFloat()

        val labelBand = 16.dp.toPx()
        val reviewTop = 22.dp.toPx(); val reviewH = 10.dp.toPx()
        val recTop = 36.dp.toPx(); val recH = h - recTop - 8.dp.toPx()

        drawRect(cBg)

        // Future (after now) shaded.
        val nowT = now()
        if (nowT < t1) drawRect(cFuture, topLeft = Offset(x(nowT), 0f), size = Size(w - x(nowT), h))

        // --- ticks + labels ---
        val step = pickStep(spp)
        val tzOff = TimeZone.getDefault().getOffset((center * 1000).toLong()) / 1000.0
        val majorEvery = Math.round(step.major / step.minor)
        var k = floor((t0 + tzOff) / step.minor).toLong()
        while (true) {
            val t = k * step.minor - tzOff
            if (t > t1 + step.major) break
            val px = x(t)
            if (Math.floorMod(k, majorEvery) == 0L) {
                drawLine(cMuted.copy(alpha = 0.5f), Offset(px, labelBand), Offset(px, h), 1f)
                val localSec = Math.floorMod(Math.round(k * step.minor), 86400L)
                val midnight = localSec == 0L
                val d = Date(Math.round(t * 1000))
                val text = when {
                    midnight -> fmtDay.format(d)
                    step.major < 60 -> fmtSec.format(d)
                    else -> fmtMin.format(d)
                }
                val layout = measurer.measure(text, if (midnight) dayStyle else labelStyle)
                drawText(layout, topLeft = Offset(px - layout.size.width / 2f, 1f))
            } else {
                drawLine(cLine, Offset(px, labelBand + 2f), Offset(px, labelBand + 7.dp.toPx()), 1f)
            }
            k++
        }

        // --- recordings + motion ---
        val firstIdx = firstVisible(segments, t0)
        val maxMotion = max(1, segments.maxOfOrNull { it.motion } ?: 1)
        var covStart = Double.NaN; var covEnd = Double.NaN
        fun flushCov() {
            if (!covStart.isNaN()) {
                val a = max(0f, x(covStart)); val b = min(w, x(covEnd))
                if (b > a) drawRoundRect(st.recording.copy(alpha = 0.30f), Offset(a, recTop), Size(max(1f, b - a), recH), CornerRadius(3f))
            }
        }
        for (i in firstIdx until segments.size) {
            val s = segments[i]
            if (s.start > t1) break
            if (covStart.isNaN() || s.start - covEnd > 2.5) { flushCov(); covStart = s.start }
            covEnd = s.end
        }
        flushCov()
        for (i in firstIdx until segments.size) {
            val s = segments[i]
            if (s.start > t1) break
            if (s.motion <= 0) continue
            val a = x(s.start); val b = x(s.end)
            val bh = recH * (0.25f + 0.75f * (s.motion.toFloat() / maxMotion))
            drawRect(st.motion.copy(alpha = 0.75f), Offset(a, recTop + recH - bh), Size(max(1f, b - a - 0.5f), bh))
        }

        // --- review items ---
        for (r in reviews) {
            val rs = r.start; val re = r.end ?: nowT
            if (re < t0 || rs > t1) continue
            val a = x(rs); val b = max(a + 3.dp.toPx(), x(re))
            drawRoundRect(
                if (r.isAlert) st.alert else st.detection,
                Offset(a, reviewTop), Size(b - a, reviewH), CornerRadius(reviewH / 2),
            )
        }

        // --- selection ---
        selection?.let { (a0, b0) ->
            val a = x(minOf(a0, b0)).coerceIn(0f, w); val b = x(maxOf(a0, b0)).coerceIn(0f, w)
            drawRect(cSel.copy(alpha = 0.22f), Offset(a, labelBand), Size(max(2f, b - a), h - labelBand))
            drawLine(cSel, Offset(a, labelBand), Offset(a, h), 2.dp.toPx())
            drawLine(cSel, Offset(b, labelBand), Offset(b, h), 2.dp.toPx())
        }

        // --- playhead ---
        val cx = w / 2f
        drawLine(cHead, Offset(cx, labelBand - 2f), Offset(cx, h), 2.dp.toPx())
        val tri = Path().apply {
            moveTo(cx - 6.dp.toPx(), labelBand - 4.dp.toPx())
            lineTo(cx + 6.dp.toPx(), labelBand - 4.dp.toPx())
            lineTo(cx, labelBand + 4.dp.toPx())
            close()
        }
        drawPath(tri, cHead)
    }
}

private fun pickStep(spp: Double): TickStep {
    val minPx = 120.0
    return tickSteps.firstOrNull { it.major / spp >= minPx } ?: tickSteps.last()
}

private fun firstVisible(segs: List<RecordingSegment>, t0: Double): Int {
    var lo = 0; var hi = segs.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (segs[mid].end < t0) lo = mid + 1 else hi = mid
    }
    return lo
}
