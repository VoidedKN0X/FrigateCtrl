package com.vknxworks.frigatectrl.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.vknxworks.frigatectrl.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Opt-in, anonymous usage stats sent to the project's own self-hosted Aptabase server.
 *
 * Nothing is sent unless the user turned "Share anonymous usage stats" on, and builds without an
 * Aptabase key (anyone building from source) can't send anything at all.
 *
 * What an event contains: its name, a few fixed-value properties chosen in code (a screen's kind such as
 * "Live" or "History", "events_list" or "skip_button"), the app version, the Android version, the phone
 * model, the system language and a random session ID that changes every launch or after an hour of inactivity.
 * Never sent: the server address, username, camera names, zones, detected objects or labels, event IDs,
 * recording times, or any persistent device or install ID. (Like any web request, the stats server sees
 * the phone's IP address; Aptabase uses it for a country lookup and doesn't store it.)
 */
class Telemetry(private val context: Context, private val settings: Settings) {
    /** Whether this build can send stats at all (it has a key and an HTTPS server address). */
    val available: Boolean = BuildConfig.APTABASE_KEY.startsWith("A-SH-") && BuildConfig.APTABASE_HOST.startsWith("https://")

    // A separate client from the Frigate one: no login interceptor, cookies, pinned certificate or redirects.
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private val systemProps: JSONObject by lazy {
        JSONObject()
            .put("isDebug", BuildConfig.DEBUG)
            .put("osName", "Android")
            .put("osVersion", Build.VERSION.RELEASE ?: "")
            .put("locale", Locale.getDefault().language)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("appBuildNumber", BuildConfig.VERSION_CODE.toString())
            .put("sdkVersion", "frigatectrl@${BuildConfig.VERSION_NAME}")
            .put("deviceModel", Build.MODEL ?: "")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<JSONObject>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var sessionId = newSessionId()
    @Volatile private var lastEventAt = 0L
    /** The request being sent right now, so switching stats off can stop it. */
    @Volatile private var inFlight: Call? = null

    init {
        // One event at a time, in order. Failures are dropped: stats are never stored or retried.
        if (available) scope.launch { for (event in queue) send(event) }
    }

    private val enabled: Boolean
        get() = available && settings.usageStats

    /** Called when the app's screen is opened (not on rotation). The settings are read synchronously, so no wait. */
    fun appOpened() = trackOpened()

    private fun trackOpened() =
        track("app_opened", mapOf("form_factor" to formFactor(), "sub_stream" to settings.preferSubStream.toString()))

    /**
     * The answer from the first-run question or the Settings switch. Switching on starts a fresh session,
     * so stats from before and after aren't linked.
     */
    fun setOptIn(on: Boolean) {
        val was = settings.usageStats
        settings.usageStats = on
        settings.usageStatsAsked = true
        if (!on) dropPending()
        if (on && !was && available) {
            resetSession()
            trackOpened()
        }
    }

    /** Switched off: nothing queued goes out, and a request being sent is stopped. */
    private fun dropPending() {
        while (queue.tryReceive().isSuccess) Unit
        inFlight?.cancel()
        resetSession()
    }

    /** Records an event if the user opted in. [props] values must be fixed strings or numbers from code. */
    fun track(name: String, props: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastEventAt > SESSION_TIMEOUT_MS) sessionId = newSessionId()
        lastEventAt = now
        val event = JSONObject()
            .put("timestamp", timestamp(now))
            .put("sessionId", sessionId)
            .put("eventName", name)
            .put("systemProps", systemProps)
            .put("props", JSONObject(props))
        queue.trySend(event)
    }

    private fun resetSession() {
        sessionId = newSessionId()
        lastEventAt = 0L
    }

    private fun send(event: JSONObject) {
        // The user may have opted out while this was queued.
        if (!enabled) return
        val request = Request.Builder()
            .url("${BuildConfig.APTABASE_HOST.trimEnd('/')}/api/v0/event")
            .header("App-Key", BuildConfig.APTABASE_KEY)
            .header("User-Agent", "FrigateCtrl/${BuildConfig.VERSION_NAME} (Android)")
            .post(event.toString().toRequestBody(JSON))
            .build()
        val call = http.newCall(request)
        inFlight = call
        // Re-checked here too: switching off between the check above and this point still stops it.
        if (!enabled) call.cancel()
        runCatching { call.execute().close() }
        inFlight = null
    }

    /** "phone" or "tablet", from the screen's smallest width. */
    private fun formFactor(): String {
        val sw = context.resources.configuration.smallestScreenWidthDp
        return if (sw != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED && sw >= 600) "tablet" else "phone"
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    private fun timestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    companion object {
        private val JSON = "application/json".toMediaType()
        private val SESSION_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1)
    }
}
