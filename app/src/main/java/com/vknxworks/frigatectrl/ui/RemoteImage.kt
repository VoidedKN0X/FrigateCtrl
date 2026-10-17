package com.vknxworks.frigatectrl.ui

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Thumbnails and snapshots are only ever kept in memory, never written to disk.
private val imageCache = object : LruCache<String, ImageBitmap>(48) {}

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
    LaunchedEffect(url, refreshMs) {
        if (url == null) return@LaunchedEffect
        if (refreshMs <= 0) {
            loadBitmap(url)?.let { bmp = it; onBitmap?.invoke(it) }
        } else {
            while (true) {
                val sep = if (url.contains('?')) '&' else '?'
                loadBitmap("$url${sep}_=${System.currentTimeMillis()}", useCache = false)?.let { bmp = it; onBitmap?.invoke(it) }
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
