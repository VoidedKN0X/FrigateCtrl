package com.vknxworks.frigateremote.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vknxworks.frigateremote.App
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * Native live view. Plays the camera's go2rtc stream through Frigate's /live/mse proxy:
 *  1. progressive fMP4 (api/stream.mp4) — ~1 s latency
 *  2. HLS (api/stream.m3u8) — a few seconds latency, very tolerant
 *  3. snapshots refreshed twice a second
 * A snapshot is shown on top until the first real video frame renders, so it's never black.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveView(camera: String, streamName: String, muted: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var attempt by remember(camera, streamName) { mutableIntStateOf(0) }
    var mode by remember(camera, streamName, attempt) { mutableIntStateOf(0) } // 0 mp4, 1 hls, 2 snapshots
    var notes by remember(camera, streamName, attempt) { mutableStateOf(listOf<String>()) }
    var rendered by remember(camera, streamName, attempt, mode) { mutableStateOf(false) }

    val player = remember(camera, streamName, attempt, mode) { if (mode < 2) buildLivePlayer(ctx) else null }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val label = if (mode == 0) "MP4" else "HLS"
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onPlayerError(error: PlaybackException) {
                if (rendered && mode < 2) {
                    // Was working and dropped — just reconnect the same way.
                    attempt++
                } else {
                    notes = notes + "$label: ${describe(error)}"
                    mode++
                }
            }
        }
        player.addListener(listener)
        val src = URLEncoder.encode(streamName, "UTF-8")
        val ds = OkHttpDataSource.Factory(App.api.http)
        val source = if (mode == 0) {
            ProgressiveMediaSource.Factory(ds)
                .createMediaSource(MediaItem.fromUri(App.api.url("/live/mse/api/stream.mp4?src=$src")))
        } else {
            HlsMediaSource.Factory(ds)
                .createMediaSource(MediaItem.fromUri(App.api.url("/live/mse/api/stream.m3u8?src=$src&mp4")))
        }
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, muted) { player?.volume = if (muted) 0f else 1f }

    // No picture after a while → try the next method.
    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        delay(if (mode == 0) 10_000 else 15_000)
        if (!rendered) {
            notes = notes + "${if (mode == 0) "MP4" else "HLS"}: no video frames received"
            mode++
        }
    }

    // Stay close to the live edge: speed up a little if we've fallen behind.
    LaunchedEffect(player, rendered) {
        if (player == null || !rendered) return@LaunchedEffect
        while (true) {
            delay(1000)
            val behind = player.bufferedPosition - player.currentPosition
            player.setPlaybackSpeed(if (mode == 0 && behind > 1500) 1.15f else 1f)
        }
    }

    Box(modifier.background(Color.Black)) {
        if (player != null) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!rendered) {
            RemoteImage(
                App.api.latestJpg(camera, 720), Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, refreshMs = 500,
            )
        }
        if (mode >= 2) {
            Column(
                Modifier.align(Alignment.BottomStart).padding(8.dp).widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000))
                    .clickable { attempt++ }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Snapshot mode · tap to retry video", color = Color.White, fontSize = 12.sp)
                notes.forEach { Text(it, color = Color(0xFFB9C2CE), fontSize = 11.sp, maxLines = 2) }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun buildLivePlayer(ctx: Context): ExoPlayer {
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(500, 3000, 250, 250)
        .build()
    return ExoPlayer.Builder(ctx).setLoadControl(load).build()
}

@OptIn(UnstableApi::class)
private fun describe(e: PlaybackException): String {
    var c: Throwable? = e
    while (c != null) {
        if (c is HttpDataSource.InvalidResponseCodeException) {
            return when (c.responseCode) {
                401, 403 -> "login rejected (HTTP ${c.responseCode})"
                404 -> "stream not found (HTTP 404) — check the go2rtc stream name"
                else -> "HTTP ${c.responseCode}"
            }
        }
        c = c.cause
    }
    return e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')
}
