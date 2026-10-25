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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
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

    val player = remember(camera, streamName, attempt, mode, background) {
        if (mode < 1 && !background) buildLivePlayer(ctx) else null
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val label = ctx.getString(R.string.video)
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onPlaybackStateChanged(state: Int) {
                // A live stream never really ends: if it does, the connection was dropped. Reconnect instead of
                // staying frozen on the last frame (or move on to snapshots if it never showed a picture).
                if (state == Player.STATE_ENDED) {
                    if (rendered) attempt++ else mode++
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (rendered) {
                    // Was working and dropped — just reconnect the same way.
                    attempt++
                } else {
                    notes = notes + "$label: ${describe(ctx, error)}"
                    mode++
                }
            }
        }
        player.addListener(listener)
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
            player.release()
        }
    }

    LaunchedEffect(player, muted) { player?.volume = if (muted) 0f else 1f }

    // Stop streaming (and sound) while the app is in the background; reconnect at the live edge when it returns.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { background = true }
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
        while (true) {
            delay(1000)
            val behind = player.bufferedPosition - player.currentPosition
            player.setPlaybackSpeed(if (behind > 1500) 1.15f else 1f)
            val pos = player.currentPosition
            if (player.playWhenReady && pos == lastPos) {
                if (stuckSince == 0L) stuckSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - stuckSince >= STALL_MS) {
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
        }
    }
}

/** How long the live picture may stand still (while it should be playing) before reconnecting. */
private const val STALL_MS = 8_000L

@OptIn(UnstableApi::class)
private fun buildLivePlayer(ctx: Context): ExoPlayer {
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(500, 3000, 250, 250)
        .build()
    return ExoPlayer.Builder(ctx).setLoadControl(load).build()
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
