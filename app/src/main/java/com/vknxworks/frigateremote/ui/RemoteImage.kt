package com.vknxworks.frigateremote.ui

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.vknxworks.frigateremote.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val imageCache = object : LruCache<String, ImageBitmap>(48) {}

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
    placeholder: @Composable () -> Unit = {},
) {
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, refreshMs) {
        if (url == null) return@LaunchedEffect
        if (refreshMs <= 0) {
            loadBitmap(url)?.let { bmp = it }
        } else {
            while (true) {
                val sep = if (url.contains('?')) '&' else '?'
                loadBitmap("$url${sep}_=${System.currentTimeMillis()}", useCache = false)?.let { bmp = it }
                delay(refreshMs)
            }
        }
    }
    val b = bmp
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = contentScale)
    else Box(modifier) { placeholder() }
}
