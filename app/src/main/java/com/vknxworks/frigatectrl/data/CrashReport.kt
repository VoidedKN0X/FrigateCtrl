package com.vknxworks.frigatectrl.data

import android.content.Context
import android.os.Build
import com.vknxworks.frigatectrl.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last crash, kept on this phone only. When FrigateCtrl stops because of an error, its stack trace (with
 * the time, the app and Android version and the phone model) is written to a private file, which Settings
 * shows under Troubleshooting so it can be copied into a bug report. Nothing is ever sent anywhere.
 */
object CrashReport {
    private const val MAX_CHARS = 32 * 1024

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, "last_crash.txt")

    /** Installs the handler once; Android's own handler still runs afterwards (the "has stopped" dialog). */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Recorder) return
        Thread.setDefaultUncaughtExceptionHandler(Recorder(app, previous))
    }

    private class Recorder(
        private val app: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try { write(app, t, e) } catch (_: Throwable) {}
            if (previous != null) {
                previous.uncaughtException(t, e)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun write(context: Context, t: Thread, e: Throwable) {
        val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val text = buildString {
            appendLine("FrigateCtrl ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Time: $time")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${t.name}")
            appendLine()
            append(scrub(trace))
        }
        file(context).writeText(if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "\n…" else text)
    }

    private val urlUserInfo = Regex("((?:https?|wss?)://)[^/\\s@]+@", RegexOption.IGNORE_CASE)
    private val urlQuery = Regex("((?:https?|wss?)://[^\\s?#]*)[?#][^\\s]*", RegexOption.IGNORE_CASE)
    private val secrets = Regex("((?:frigate_token|token|password|secret|cookie)[=:]\\s*)[^\\s;&,]+", RegexOption.IGNORE_CASE)

    /** No logins in the report: user:password@ and query strings are cut from addresses, tokens are masked. */
    private fun scrub(s: String): String =
        s.replace(urlUserInfo, "$1").replace(urlQuery, "$1").replace(secrets, "$1…")

    /** The last crash report, or null when there is none. */
    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    /** When the last crash happened (milliseconds), or 0. */
    fun time(context: Context): Long = runCatching { file(context).lastModified() }.getOrDefault(0L)

    fun delete(context: Context) {
        runCatching { file(context).delete() }
    }
}
