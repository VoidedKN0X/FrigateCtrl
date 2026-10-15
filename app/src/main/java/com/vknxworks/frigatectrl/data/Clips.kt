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

/** Downloads recording clips from Frigate and saves them to the gallery or shares them. */
object Clips {
    const val MAX_SECONDS = 3600.0

    private fun fileName(camera: String, start: Double): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date((start * 1000).toLong()))
        return "${camera}_$stamp.mp4"
    }

    /** Download [start]..[end] of [camera] into the app's private cache. */
    private suspend fun download(context: Context, camera: String, start: Double, end: Double): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "clips").apply { mkdirs() }
            // Old shared clips are only needed until the receiving app has read them.
            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.delete() }
            val out = File(dir, fileName(camera, start))
            // Frigate stitches the clip together on request, which can take a while for long ranges.
            val client = App.api.http.newBuilder().readTimeout(5, TimeUnit.MINUTES).build()
            client.newCall(Request.Builder().url(App.api.clipUrl(camera, start, end)).build()).execute().use { r ->
                if (!r.isSuccessful) throw Exception(if (r.code == 404) "No recordings in that range" else "HTTP ${r.code}")
                val body = r.body ?: throw Exception("Empty response")
                out.outputStream().use { body.byteStream().copyTo(it) }
            }
            if (out.length() < 1024) { out.delete(); throw Exception("No recordings in that range") }
            out
        }

    /** Saves the clip to Movies/FrigateCtrl. Returns the display name. */
    suspend fun save(context: Context, camera: String, start: Double, end: Double): String {
        val file = download(context, camera, start, end)
        return withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FrigateCtrl")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Couldn't create the file")
            resolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            file.delete()
            file.name
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
        context.startActivity(Intent.createChooser(send, "Share clip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
