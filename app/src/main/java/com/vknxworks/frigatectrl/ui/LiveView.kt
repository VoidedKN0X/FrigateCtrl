package com.vknxworks.frigatectrl.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import com.vknxworks.frigatectrl.data.LiveReport
import com.vknxworks.frigatectrl.data.MseWebSocketDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vknxworks.frigatectrl.App
import kotlinx.coroutines.delay
import java.net.URLEncoder
import androidx.compose.ui.res.stringResource
import com.vknxworks.frigatectrl.R

/**
 * Native live view: go2rtc's fMP4 stream read from Frigate's /live/mse websocket and played by
 * ExoPlayer (hardware H.264/H.265). Falls back to snapshots refreshed twice a second.
 * A snapshot is shown on top until the first real video frame renders, so it's never black.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveView(
    camera: String,
    streamName: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
    zoom: ZoomState = remember { ZoomState() },
    showNotes: Boolean = true,
) {
    val ctx = LocalContext.current
    var attempt by remember(camera, streamName) { mutableIntStateOf(0) }
    var mode by remember(camera, streamName, attempt) { mutableIntStateOf(0) } // 0 video, 1 snapshots
    var notes by remember(camera, streamName, attempt) { mutableStateOf(listOf<String>()) }
    // In the background (not in picture-in-picture) the stream is dropped, not just paused: a paused player stops
    // reading while the camera keeps sending, so coming back would play old video and stay behind. Coming back
    // starts a fresh connection at the live edge.
    var background by remember { mutableStateOf(false) }
    var rendered by remember(camera, streamName, attempt, mode, background) { mutableStateOf(false) }
    // Some phones' video decoders get stuck while the sound keeps going. After two such freezes on this camera,
    // the live view plays without sound (no audio decoder, video on the system clock) until the screen is left.
    var frameStalls by remember(camera, streamName) { mutableIntStateOf(0) }
    var audioForcedOff by remember(camera, streamName) { mutableStateOf(false) }
    var audioNoteDismissed by remember(camera, streamName) { mutableStateOf(false) }
    // Muted: the audio track is turned off entirely, so no audio decoder runs and the video doesn't depend on it.
    val audioOff = muted || audioForcedOff
    // On-device report about this live view (decoders, formats, reconnects); never sent anywhere.
    val report = remember(camera, streamName) { LiveReport.Session(camera) }
    report.muted = muted
    report.audioOffAfterFreezes = audioForcedOff
    DisposableEffect(report) { onDispose { report.save(ctx) } }

    val player = remember(camera, streamName, attempt, mode, background) {
        if (mode < 1 && !background) buildLivePlayer(ctx, audioOff) else null
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        report.markUsed()
        val label = ctx.getString(R.string.video)
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onPlaybackStateChanged(state: Int) {
                // A live stream never really ends: if it does, the connection was dropped. Reconnect instead of
                // staying frozen on the last frame (or move on to snapshots if it never showed a picture).
                if (state == Player.STATE_ENDED) {
                    if (rendered) {
                        report.reconnect("stream ended (closed by the server)")
                        report.save(ctx)
                        attempt++
                    } else {
                        report.reconnect("stream ended before the first frame: snapshots")
                        report.save(ctx)
                        mode++
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (rendered) {
                    // Was working and dropped — just reconnect the same way.
                    report.reconnect("error: ${reportReason(error)}")
                    report.save(ctx)
                    attempt++
                } else {
                    report.reconnect("error before the first frame: ${reportReason(error)}: snapshots")
                    report.save(ctx)
                    notes = notes + "$label: ${describe(ctx, error)}"
                    mode++
                }
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) { report.videoDecoder = decoderName }
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) { report.audioDecoder = decoderName }
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) { report.videoFormat = videoFormatText(format) }
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) { report.audioFormat = audioFormatText(format) }
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                report.droppedFrames += droppedFrames
            }
        }
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        val src = URLEncoder.encode(streamName, "UTF-8")
        // Frigate only exposes go2rtc's live video as the MSE websocket, so read fMP4 straight from it.
        val source = ProgressiveMediaSource.Factory(
            MseWebSocketDataSource.Factory(App.api.http),
            ExtractorsFactory { arrayOf<Extractor>(FragmentedMp4Extractor()) },
        ).createMediaSource(MediaItem.fromUri(App.api.url("/live/mse/api/ws?src=$src")))
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
            player.release()
        }
    }

    LaunchedEffect(player, muted) { player?.volume = if (muted) 0f else 1f }
    LaunchedEffect(player, audioOff) {
        val p = player ?: return@LaunchedEffect
        val params = p.trackSelectionParameters
        val disabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)
        if (audioOff && !disabled) {
            // Muting: drop the audio track while playing.
            p.trackSelectionParameters = params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        } else if (!audioOff && disabled) {
            // Unmuting: a live stream can't seek back to pick the sound up again, so start a fresh connection
            // with sound at the live edge.
            attempt++
        }
    }

    // Stop streaming (and sound) while the app is in the background; reconnect at the live edge when it returns.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { background = true; report.save(ctx) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { background = false }

    // No picture after a while → try the next method.
    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        delay(15_000)
        if (!rendered) {
            notes = notes + ctx.getString(R.string.video_no_frames)
            mode++
        }
    }

    // Stay close to the live edge: speed up a little if we've fallen behind. And if the picture stops moving
    // without any error (a stalled connection or a stuck hardware decoder, which some phones do), reconnect.
    LaunchedEffect(player, rendered) {
        if (player == null || !rendered) return@LaunchedEffect
        var lastPos = player.currentPosition
        var stuckSince = 0L
        // Rendered video frames: with sound, the audio clock keeps the position moving even when the video
        // decoder is stuck, so the position alone doesn't show a frozen picture.
        var counters = player.videoDecoderCounters
        var lastFrames = counters?.let { it.ensureUpdated(); it.renderedOutputBufferCount } ?: -1
        var framesSince = System.currentTimeMillis()
        var speed = 1f
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            // Speed up above 2 s behind and back to normal only below 0.7 s, and change it only when it really
            // changes: switching every second reconfigures the audio output, which some phones handle badly.
            val behind = player.bufferedPosition - player.currentPosition
            val wanted = when {
                behind > CATCH_UP_ABOVE_MS -> CATCH_UP_SPEED
                behind < CATCH_UP_BELOW_MS -> 1f
                else -> speed
            }
            if (wanted != speed) {
                speed = wanted
                player.setPlaybackSpeed(wanted)
            }

            val active = player.playWhenReady &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            val c = player.videoDecoderCounters
            val frames = c?.let { it.ensureUpdated(); it.renderedOutputBufferCount } ?: -1
            if (c == null || c !== counters || frames != lastFrames || !active) {
                counters = c
                lastFrames = frames
                framesSince = now
            } else if (now - framesSince >= FRAME_STALL_MS) {
                // Only a freeze while the player had data (not one while waiting for the network) counts towards
                // turning the sound off.
                val decoderStuck = player.playbackState == Player.STATE_READY
                if (decoderStuck) frameStalls++
                report.reconnect(
                    "no new video frame for ${FRAME_STALL_MS / 1000} s " +
                        if (decoderStuck) "(frame stall $frameStalls)" else "(while buffering)"
                )
                if (frameStalls >= 2 && !audioForcedOff && !muted) {
                    audioForcedOff = true
                    report.audioOffAfterFreezes = true
                    report.reconnect("sound turned off for this camera after repeated frame stalls")
                }
                report.save(ctx)
                attempt++
                return@LaunchedEffect
            }

            val pos = player.currentPosition
            if (player.playWhenReady && pos == lastPos) {
                if (stuckSince == 0L) stuckSince = now
                if (now - stuckSince >= STALL_MS) {
                    report.reconnect("position stopped for ${STALL_MS / 1000} s")
                    report.save(ctx)
                    attempt++
                    return@LaunchedEffect
                }
            } else {
                stuckSince = 0L
            }
            lastPos = pos
        }
    }

    Box(modifier.background(Color.Black)) {
        if (player != null) PlayerSurface(player, zoom)
        if (!rendered) {
            RemoteImage(
                App.api.latestJpg(camera, 720),
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.scale; scaleY = zoom.scale
                    translationX = zoom.offset.x; translationY = zoom.offset.y
                },
                contentScale = ContentScale.Fit, refreshMs = 500,
            )
        }
        if (mode >= 1 && showNotes) {
            Column(
                Modifier.align(Alignment.BottomStart).padding(8.dp).widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000))
                    .clickable { attempt++ }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.snapshot_mode), color = Color.White, fontSize = 12.sp)
                notes.forEach { Text(it, color = Color(0xFFB9C2CE), fontSize = 11.sp, maxLines = 2) }
            }
        } else if (audioForcedOff && !muted && !audioNoteDismissed && showNotes) {
            Column(
                Modifier.align(Alignment.BottomStart).padding(8.dp).widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000))
                    .clickable { audioNoteDismissed = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.live_sound_off_freezes), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/** How long the live picture may stand still (while it should be playing) before reconnecting. */
private const val STALL_MS = 8_000L

/** How long no new video frame may be shown (while it should be playing) before reconnecting. */
private const val FRAME_STALL_MS = 5_000L

/** Catch up with the live edge above this much buffered video, back to normal speed below the second value. */
private const val CATCH_UP_ABOVE_MS = 2_000L
private const val CATCH_UP_BELOW_MS = 700L
private const val CATCH_UP_SPEED = 1.15f

@OptIn(UnstableApi::class)
private fun buildLivePlayer(ctx: Context, audioOff: Boolean): ExoPlayer {
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(500, 3000, 250, 250)
        .build()
    // Fall back to another decoder when the first one fails to start (some phones list a decoder that can't
    // handle the camera's stream).
    val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
    val player = ExoPlayer.Builder(ctx, renderers).setLoadControl(load).build()
    // Without sound (muted, or turned off after freezes) no audio decoder runs and the video plays on the
    // system clock instead of depending on the audio output.
    if (audioOff) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
    }
    return player
}

private fun videoFormatText(f: Format): String = buildString {
    append(f.sampleMimeType ?: "?")
    f.codecs?.let { append(' ').append(it) }
    if (f.width > 0 && f.height > 0) append(' ').append(f.width).append('x').append(f.height)
    if (f.frameRate > 0f) append(' ').append(String.format(java.util.Locale.US, "%.1f fps", f.frameRate))
}

private fun audioFormatText(f: Format): String = buildString {
    append(f.sampleMimeType ?: "?")
    f.codecs?.let { append(' ').append(it) }
    if (f.sampleRate > 0) append(' ').append(f.sampleRate).append(" Hz")
    if (f.channelCount > 0) append(' ').append(f.channelCount).append(" ch")
}

/**
 * Why playback stopped, for the live view report. Only messages FrigateCtrl itself writes are kept, never
 * messages from the network stack, which can name the server.
 */
@OptIn(UnstableApi::class)
private fun reportReason(e: PlaybackException): String {
    val code = e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')
    var c: Throwable? = e
    while (c != null) {
        if (c is HttpDataSource.InvalidResponseCodeException) return "$code (HTTP ${c.responseCode})"
        val m = c.message
        if (m != null) {
            when {
                m == "the live stream was closed by the server" || m == "the live stream fell behind" ||
                    m == "no data from the live stream" || m == "connection failed" ||
                    m.startsWith("login rejected") || m.startsWith("stream not found") ||
                    m.matches(Regex("HTTP \\d+")) -> return "$code ($m)"
                m.startsWith("go2rtc:") -> return "$code (go2rtc error)"
            }
        }
        c = c.cause
    }
    val root = generateSequence(e as Throwable) { it.cause }.last()
    return if (root === e) code else "$code (${root.javaClass.simpleName})"
}

@OptIn(UnstableApi::class)
private fun describe(ctx: Context, e: PlaybackException): String {
    var c: Throwable? = e
    while (c != null) {
        if (c is HttpDataSource.InvalidResponseCodeException) {
            return when (c.responseCode) {
                401, 403 -> ctx.getString(R.string.video_login_rejected, c.responseCode)
                404 -> ctx.getString(R.string.video_stream_not_found)
                else -> ctx.getString(R.string.error_http, c.responseCode)
            }
        }
        c = c.cause
    }
    return e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')
}
