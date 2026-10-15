package com.vknxworks.frigateremote.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.vknxworks.frigateremote.App
import com.vknxworks.frigateremote.data.RecordingSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Plays Frigate recordings through its VOD HLS endpoint (/vod/<cam>/start/<s>/end/<e>/index.m3u8)
 * in one-hour windows, and maps between wall-clock time and player position. Frigate's VOD skips
 * gaps between recordings, so the mapping walks the segment list exactly like the web UI does.
 */
@OptIn(UnstableApi::class)
class Playback(context: Context, private val camera: String, private val scope: CoroutineScope) {

    private class Clip(val start: Double, val durMs: Long)

    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val dataSource = OkHttpDataSource.Factory(App.api.http)

    private var winStart = 0L
    private var winEnd = 0L
    private var clips: List<Clip> = emptyList()
    private var loadJob: Job? = null

    var loading by mutableStateOf(false)
        private set
    var noRecording by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Set when we jump past the end of a window so playback continues seamlessly. */
    var onWindowEnded: ((Double) -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onWindowEnded?.invoke(winEnd.toDouble())
                if (state == Player.STATE_READY) loading = false
                if (state == Player.STATE_BUFFERING) loading = true
            }
            override fun onPlayerError(e: PlaybackException) {
                loading = false
                error = "Playback error: ${e.errorCodeName}"
            }
        })
    }

    val hasMedia get() = clips.isNotEmpty()

    fun inWindow(t: Double) = clips.isNotEmpty() && t >= winStart && t < winEnd - 1

    /** Wall-clock time currently shown by the player, or null if nothing is loaded. */
    fun currentTime(): Double? {
        if (clips.isEmpty()) return null
        return timeAt(player.currentPosition)
    }

    /** Seek to wall-clock [t]. Loads a new VOD window if [t] is outside the current one. */
    fun seek(t: Double, play: Boolean, fast: Boolean = false) {
        error = null
        if (inWindow(t)) {
            player.setSeekParameters(if (fast) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
            player.seekTo(positionAt(t))
            if (!fast) player.playWhenReady = play
            noRecording = false
            return
        }
        if (fast) return
        loadWindow(t, play)
    }

    private fun loadWindow(t: Double, play: Boolean) {
        loadJob?.cancel()
        loading = true
        noRecording = false
        loadJob = scope.launch {
            val now = System.currentTimeMillis() / 1000.0
            val ws = floor(t).toLong() - 600
            val we = min(ws + 3600, floor(now).toLong())
            val segs = try {
                App.api.recordings(camera, ws.toDouble(), we.toDouble())
            } catch (e: Exception) {
                loading = false
                error = e.message
                return@launch
            }
            val newClips = buildClips(segs, ws.toDouble(), we.toDouble())
            if (newClips.isEmpty() || newClips.last().let { it.start + it.durMs / 1000.0 } < t) {
                loading = false
                noRecording = true
                player.pause()
                return@launch
            }
            winStart = ws; winEnd = we; clips = newClips
            val src = HlsMediaSource.Factory(dataSource)
                .createMediaSource(MediaItem.fromUri(App.api.vodUrl(camera, ws, we)))
            player.setSeekParameters(SeekParameters.EXACT)
            player.setMediaSource(src, positionAt(t))
            player.prepare()
            player.playWhenReady = play
        }
    }

    private fun buildClips(segs: List<RecordingSegment>, ws: Double, we: Double): List<Clip> {
        val out = ArrayList<Clip>(segs.size)
        for (s in segs) {
            if (s.end <= ws || s.start >= we) continue
            var d = if (s.duration.isNaN() || s.duration <= 0) s.end - s.start else s.duration
            if (ws > s.start) d -= (ws - s.start)
            if (s.end > we) d -= (s.end - we)
            val ms = (d * 1000).toLong()
            if (ms <= 0) continue
            out += Clip(max(s.start, ws), ms)
        }
        return out
    }

    private fun positionAt(t: Double): Long {
        var acc = 0L
        for (c in clips) {
            val endT = c.start + c.durMs / 1000.0
            if (t < c.start) return acc               // in a gap → next recording
            if (t < endT) return acc + ((t - c.start) * 1000).toLong()
            acc += c.durMs
        }
        return max(0L, acc - 1)
    }

    private fun timeAt(posMs: Long): Double {
        var acc = 0L
        for (c in clips) {
            if (posMs < acc + c.durMs) return c.start + (posMs - acc) / 1000.0
            acc += c.durMs
        }
        return clips.last().let { it.start + it.durMs / 1000.0 }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun pause() = player.pause()

    fun cycleSpeed() {
        val speeds = listOf(1f, 2f, 4f, 8f, 16f, 0.5f)
        speed = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
        player.setPlaybackSpeed(speed)
    }

    fun release() {
        loadJob?.cancel()
        player.release()
    }
}
