package com.vknxworks.frigatectrl.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.vknxworks.frigatectrl.App
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Thumbnails and snapshots are only ever kept in memory, never written to disk. The cache is limited by memory
// (an eighth of the app's heap, at most 64 MB) rather than by count, so a few full-size snapshots can't fill it up.
private val imageCache = object : LruCache<String, ImageBitmap>(
    (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(64L * 1024 * 1024).toInt(),
) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
}

/** Drops every cached camera image (on logout). */
fun clearImageCache() = imageCache.evictAll()

suspend fun loadBitmap(url: String, useCache: Boolean = true): ImageBitmap? {
    if (useCache) imageCache.get(url)?.let { return it }
    val bytes = App.api.bytes(url) ?: return null
    val bmp = withContext(Dispatchers.Default) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } ?: return null
    if (useCache) imageCache.put(url, bmp)
    return bmp
}

/**
 * Decodes the frames of one quickly refreshing picture (the two-a-second snapshots shown until live video
 * plays) into [BUFFERS] bitmaps used in turn, instead of a new full-size bitmap for every frame. The bitmap
 * on screen is never the one being written: with three in turn, a bitmap comes up for reuse only after the
 * two frames after it were decoded and shown. A frame of another size, or anything else Android can't decode
 * into an old bitmap, gets a fresh one. Not thread-safe: one frame at a time, for one picture, and the
 * bitmaps must not be handed to anything that keeps them (the cache, a caller).
 */
private class RefreshFrames {
    private val buffers = arrayOfNulls<Bitmap>(BUFFERS)
    private var next = 0

    fun decode(jpeg: ByteArray): Bitmap? {
        val out = decodeInto(jpeg, buffers[next]) ?: BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        // Only a bitmap that can be written again is kept for reuse.
        buffers[next] = out.takeIf { it.isMutable }
        next = (next + 1) % BUFFERS
        return out
    }

    private fun decodeInto(jpeg: ByteArray, reuse: Bitmap?): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply {
            inMutable = true
            inSampleSize = 1
            inBitmap = reuse
        }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
    }.getOrNull()

    private companion object {
        const val BUFFERS = 3
    }
}

/** One uncached frame of a refreshing picture, decoded into [frames]' bitmaps. */
private suspend fun loadFrame(url: String, frames: RefreshFrames): ImageBitmap? {
    val bytes = App.api.bytes(url) ?: return null
    return withContext(Dispatchers.Default) { frames.decode(bytes)?.asImageBitmap() }
}

/** Pictures refreshed at least this often reuse their bitmaps ([RefreshFrames]); slower ones are few and far between. */
private const val REUSE_UP_TO_MS = 1000L

/**
 * Loads an image through the authenticated client. Keeps showing the previous frame while the
 * next one loads, so refreshing snapshots never flash.
 * @param refreshMs if > 0 the image is reloaded on this interval (uncached).
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    refreshMs: Long = 0,
    onBitmap: ((ImageBitmap) -> Unit)? = null,
    placeholder: @Composable () -> Unit = {},
) {
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    // Bitmaps are reused only for a fast refresh whose frames go nowhere but this picture.
    val frames = remember { RefreshFrames() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(url, refreshMs) {
        if (url == null) return@LaunchedEffect
        if (refreshMs <= 0) {
            loadBitmap(url)?.let { bmp = it; onBitmap?.invoke(it) }
        } else {
            while (true) {
                lifecycle.awaitStarted()
                val sep = if (url.contains('?')) '&' else '?'
                val frameUrl = "$url${sep}_=${System.currentTimeMillis()}"
                if (onBitmap == null && refreshMs <= REUSE_UP_TO_MS) {
                    loadFrame(frameUrl, frames)?.let { bmp = it }
                } else {
                    loadBitmap(frameUrl, useCache = false)?.let { bmp = it; onBitmap?.invoke(it) }
                }
                delay(refreshMs)
            }
        }
    }
    val b = bmp
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = contentScale)
    else Box(modifier) { placeholder() }
}

/** Average perceived brightness (0..1) of the bottom [fraction] of the image — where labels sit. */
fun bottomBrightness(img: ImageBitmap, fraction: Float = 0.25f): Float {
    val b = img.asAndroidBitmap()
    val w = b.width; val h = b.height
    if (w == 0 || h == 0) return 0f
    val y0 = (h * (1f - fraction)).toInt().coerceIn(0, h - 1)
    var sum = 0.0; var n = 0
    for (yi in 0 until 6) {
        val y = (y0 + (h - 1 - y0) * yi / 5).coerceIn(0, h - 1)
        for (xi in 0 until 24) {
            val c = b.getPixel(((w - 1) * xi / 23), y)
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val bl = c and 0xFF
            sum += (0.2126 * r + 0.7152 * g + 0.0722 * bl) / 255.0
            n++
        }
    }
    return (sum / n).toFloat()
}
