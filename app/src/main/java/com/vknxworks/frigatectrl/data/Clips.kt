package com.vknxworks.frigatectrl.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.vknxworks.frigatectrl.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.vknxworks.frigatectrl.R

/** Downloads recording clips from Frigate and saves them to the gallery or shares them. */
object Clips {
    const val MAX_SECONDS = 3600.0

    private const val MAX_AGE_MS = 3_600_000L

    /** Camera names come from the server, so only safe characters make it into a file name. */
    private fun fileName(camera: String, start: Double): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date((start * 1000).toLong()))
        val cam = camera.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifBlank { "camera" }
        return "${cam}_$stamp.mp4"
    }

    private fun clipDir(context: Context) = File(context.cacheDir, "clips")

    /**
     * Deletes temporary clips: those older than an hour (a share target has long since read them),
     * or all of them with [all] (on logout). Called on start-up too, so footage never lingers.
     */
    fun cleanup(context: Context, all: Boolean = false) {
        runCatching {
            clipDir(context).listFiles()
                ?.filter { all || System.currentTimeMillis() - it.lastModified() > MAX_AGE_MS }
                ?.forEach { it.delete() }
        }
    }

    /** Download [start]..[end] of [camera] into the app's private cache. */
    private suspend fun download(context: Context, camera: String, start: Double, end: Double): File =
        withContext(Dispatchers.IO) {
            val dir = clipDir(context).apply { mkdirs() }
            // Old shared clips are only needed until the receiving app has read them.
            cleanup(context)
            val out = File(dir, fileName(camera, start))
            if (out.canonicalFile.parentFile != dir.canonicalFile) throw Exception(App.str(R.string.clip_error_name))
            try {
                // Frigate stitches the clip together on request, which can take a while for long ranges.
                val client = App.api.http.newBuilder().readTimeout(5, TimeUnit.MINUTES).build()
                client.newCall(Request.Builder().url(App.api.clipUrl(camera, start, end)).build()).execute().use { r ->
                    if (!r.isSuccessful) throw Exception(if (r.code == 404) App.str(R.string.clip_error_no_recordings) else App.str(R.string.error_http, r.code))
                    val body = r.body ?: throw Exception(App.str(R.string.error_empty_response))
                    out.outputStream().use { body.byteStream().copyTo(it) }
                }
                if (out.length() < 1024) throw Exception(App.str(R.string.clip_error_no_recordings))
            } catch (e: Throwable) {
                // Never leave a partial recording behind (also when the download is cancelled).
                out.delete()
                throw e
            }
            out
        }

    /** Saves the clip to Movies/FrigateCtrl. Returns the display name. */
    suspend fun save(context: Context, camera: String, start: Double, end: Double): String {
        val file = download(context, camera, start, end)
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var uri: android.net.Uri? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FrigateCtrl")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val created = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception(App.str(R.string.clip_error_create))
                uri = created
                val o = resolver.openOutputStream(created) ?: throw Exception(App.str(R.string.clip_error_write))
                o.use { file.inputStream().use { i -> i.copyTo(it) } }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(created, values, null, null)
                uri = null
                file.name
            } finally {
                // A failed save leaves neither a half-written gallery entry nor the cached copy.
                val leftover = uri
                if (leftover != null) runCatching { resolver.delete(leftover, null, null) }
                file.delete()
            }
        }
    }

    /** Downloads the clip and opens the Android share sheet. */
    suspend fun share(context: Context, camera: String, start: Double, end: Double) {
        val file = download(context, camera, start, end)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".clips", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        com.vknxworks.frigatectrl.ui.lock.LockTrips.expectReturn()
        context.startActivity(Intent.createChooser(send, context.getString(R.string.clip_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
