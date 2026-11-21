package com.vknxworks.frigatectrl.data

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One of Frigate's preview clips: a small, low frame-rate video of (usually) one hour of a camera. */
data class PreviewClip(
    /** Path under Frigate's web root, e.g. /clips/previews/front/1718000000.0-1718003600.0.mp4. */
    val src: String,
    val start: Double,
    val end: Double,
)

/** Picking the right preview for a moment. Pure, so it can be tested. */
object PreviewPick {
    /** The clip that covers [t], or null when none does (the current hour has no clip yet). */
    fun clipFor(clips: List<PreviewClip>, t: Double): PreviewClip? = clips.firstOrNull { t >= it.start && t < it.end }

    /** Where in [clip] the moment [t] is, in microseconds from its start. */
    fun offsetUs(clip: PreviewClip, t: Double): Long = ((t - clip.start).coerceAtLeast(0.0) * 1_000_000).toLong()

    /** The time in a cached preview frame's name ("preview_front-1718000000.123456.webp"), or null. */
    fun frameTime(name: String, camera: String): Double? {
        val prefix = "preview_$camera-"
        if (!name.startsWith(prefix)) return null
        return name.removePrefix(prefix).substringBeforeLast('.').toDoubleOrNull()
    }

    /** The frame taken closest before [t] (or the first one after it), from Frigate's list of cached frames. */
    fun frameFor(names: List<String>, camera: String, t: Double): String? {
        val timed = names.mapNotNull { n -> frameTime(n, camera)?.let { it to n } }.sortedBy { it.first }
        return (timed.lastOrNull { it.first <= t } ?: timed.firstOrNull())?.second
    }

    /** Only plain file names are ever put in a request path. */
    fun safeFrameName(name: String): Boolean =
        name.length <= 160 && name.all { it.isLetterOrDigit() || it in "._-" } && !name.contains("..")
}

/**
 * Pictures for scrubbing the timeline, taken from Frigate's preview clips instead of asking the server to
 * cut a frame out of the recording for every position (which takes about a second each time).
 *
 * A preview clip is downloaded once (it is small: 180 pixels high, one or two frames a second) and kept in
 * memory only, never on disk; frames are then read from it on the phone. The hour that is still being
 * recorded has no clip yet, so its picture comes from the cached preview frames Frigate keeps for it.
 */
class PreviewScrubber(private val api: FrigateApi, private val camera: String) {
    private val lock = Mutex()
    private var clips: List<PreviewClip> = emptyList()
    private var listedFrom = 0.0
    private var listedTo = 0.0
    private var loaded: PreviewClip? = null
    private var retriever: MediaMetadataRetriever? = null
    /** Clips that couldn't be used (too big, not readable): not tried again. */
    private val unusable = HashSet<String>()

    private class BytesSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val n = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, n)
            return n
        }
        override fun getSize(): Long = data.size.toLong()
        override fun close() {}
    }

    /** The preview picture for [t], or null when Frigate has no preview for that moment. */
    suspend fun frameAt(t: Double): Bitmap? = lock.withLock {
        if (t < listedFrom || t >= listedTo) {
            val from = t - LIST_SPAN
            val to = t + LIST_SPAN
            clips = runCatching { api.previews(camera, from, to) }.getOrDefault(emptyList())
            listedFrom = from
            listedTo = to
        }
        val clip = PreviewPick.clipFor(clips, t)
        if (clip != null && clip.src !in unusable) {
            fromClip(clip, t)?.let { return@withLock it }
        }
        // No clip for this moment (the hour that is still being recorded): one of the cached frames.
        val names = runCatching { api.previewFrames(camera, t - FRAME_SPAN, t + 2) }.getOrDefault(emptyList())
        val name = PreviewPick.frameFor(names, camera, t)?.takeIf { PreviewPick.safeFrameName(it) } ?: return@withLock null
        val bytes = api.bytes(api.previewFrameUrl(name)) ?: return@withLock null
        withContext(Dispatchers.Default) {
            runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    private suspend fun fromClip(clip: PreviewClip, t: Double): Bitmap? {
        if (loaded != clip) {
            closeClip()
            val bytes = api.bytes(clip.src)
            val r = if (bytes == null) null else withContext(Dispatchers.Default) {
                runCatching { MediaMetadataRetriever().apply { setDataSource(BytesSource(bytes)) } }.getOrNull()
            }
            if (r == null) {
                unusable += clip.src
                return null
            }
            retriever = r
            loaded = clip
        }
        val r = retriever ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { r.getFrameAtTime(PreviewPick.offsetUs(clip, t), MediaMetadataRetriever.OPTION_CLOSEST) }.getOrNull()
        }
    }

    private fun closeClip() {
        runCatching { retriever?.release() }
        retriever = null
        loaded = null
    }

    /** Lets go of the clip in memory. Call when the screen that scrubs is left. */
    fun release() {
        if (lock.tryLock()) {
            try { closeClip() } finally { lock.unlock() }
        }
    }

    private companion object {
        /** Preview clips are listed for this long before and after the scrubbed moment. */
        const val LIST_SPAN = 6 * 3600.0
        /** Cached frames are looked for this long before the scrubbed moment. */
        const val FRAME_SPAN = 90.0
    }
}
