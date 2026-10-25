package com.vknxworks.frigatectrl.data

import android.content.Context
import android.os.Build
import com.vknxworks.frigatectrl.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small report about the last live view, kept on this phone only, to help find out why live video misbehaves
 * on a particular phone: which decoders and formats were used, dropped frames, and every reconnect with its
 * reason. Settings shows it under Troubleshooting. No server address, URL or token is ever put in it, and it is
 * never sent anywhere.
 */
object LiveReport {
    private const val MAX_RECONNECTS = 30

    @Volatile private var last: String? = null

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, "last_live_view.txt")

    /** One live view session (one camera on screen). Used on the main thread only. */
    class Session(private val camera: String) {
        private val started = System.currentTimeMillis()
        var videoDecoder: String? = null
        var audioDecoder: String? = null
        var videoFormat: String? = null
        var audioFormat: String? = null
        var droppedFrames = 0L
        var muted = false
        var audioOffAfterFreezes = false
        private var reconnectCount = 0
        private val reconnects = ArrayList<String>()
        private var used = false

        fun markUsed() { used = true }

        fun reconnect(reason: String) {
            reconnectCount++
            if (reconnects.size < MAX_RECONNECTS) reconnects += "${clock(System.currentTimeMillis())}  $reason"
        }

        fun text(): String = buildString {
            appendLine("FrigateCtrl ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Started: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(started))}")
            appendLine("Length: ${(System.currentTimeMillis() - started) / 1000} s")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Camera: $camera")
            appendLine()
            appendLine("Video decoder: ${videoDecoder ?: "-"}")
            appendLine("Video: ${videoFormat ?: "-"}")
            appendLine("Audio decoder: ${audioDecoder ?: "-"}")
            appendLine("Audio: ${audioFormat ?: "-"}")
            appendLine(
                "Sound: " + when {
                    audioOffAfterFreezes -> "off (video kept freezing with sound)"
                    muted -> "muted (audio track off)"
                    else -> "on"
                }
            )
            appendLine("Dropped video frames: $droppedFrames")
            appendLine("Reconnects: $reconnectCount")
            reconnects.forEach { appendLine("  $it") }
            if (reconnectCount > reconnects.size) appendLine("  …")
        }

        /** Keeps this session as the last live view report, in memory and in a private file. */
        fun save(context: Context) {
            if (!used) return
            val text = text()
            last = text
            val f = file(context)
            Thread { runCatching { f.writeText(text) } }.start()
        }
    }

    private fun clock(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

    /** The last live view report, or null when there is none. */
    fun read(context: Context): String? = last
        ?: runCatching { file(context).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    /** When the last live view report was saved (milliseconds), or 0. */
    fun time(context: Context): Long = runCatching { file(context).lastModified() }.getOrDefault(0L)

    fun delete(context: Context) {
        last = null
        runCatching { file(context).delete() }
    }
}
