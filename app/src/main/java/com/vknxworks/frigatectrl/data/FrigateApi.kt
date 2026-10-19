package com.vknxworks.frigatectrl.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

data class StreamOption(val label: String, val streamName: String)

data class CameraInfo(
    val name: String,
    val enabled: Boolean,
    val streams: List<StreamOption>,
    val hasOnvif: Boolean,
    val order: Int,
    /** Audio detection is configured, so it can be toggled. */
    val hasAudioDetection: Boolean = false,
    /** Frigate notifications are configured for this camera. */
    val hasNotifications: Boolean = false,
) {
    val displayName: String get() = name.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

data class CameraGroup(val name: String, val cameras: List<String>, val order: Int)

data class RecordingSegment(
    val start: Double,
    val end: Double,
    val motion: Int,
    val objects: Int,
    val duration: Double = end - start,
)

data class ReviewItem(
    val id: String,
    val camera: String,
    val start: Double,
    val end: Double?,
    val severity: String,
    val labels: List<String>,
    val thumbPath: String?,
    val reviewed: Boolean = false,
) {
    val isAlert get() = severity == "alert"
}

data class PtzInfo(val features: Set<String>, val presets: List<String>) {
    val panTilt get() = "pt" in features
    val zoom get() = "zoom" in features
    val focus get() = "focus" in features
}

class AuthException(msg: String) : Exception(msg)

class FrigateApi(private val prefs: Prefs) {

    @Volatile var baseUrl: String = prefs.baseUrl.trimEnd('/')
        private set
    @Volatile var token: String = prefs.token
        private set
    @Volatile private var baseHttpUrl: HttpUrl? = baseUrl.toHttpUrlOrNull()

    private data class ClientKey(val base: String, val pin: String, val httpOk: Boolean)
    @Volatile private var clientCache: Pair<ClientKey, OkHttpClient>? = null

    /** The pinned certificate fingerprint in use ("" = normal certificate validation). */
    private val activePin: String get() = if (prefs.allowInsecureTls) prefs.certPin else ""

    /**
     * Shared client for everything that talks to Frigate (API, images, HLS, MSE and talk
     * websockets, clip downloads): adds the auth cookie for the server only, follows token
     * refreshes, re-logs in on 401 and applies the pinned certificate.
     */
    val http: OkHttpClient
        get() {
            val key = ClientKey(baseUrl, activePin, prefs.httpConfirmed)
            clientCache?.let { if (it.first == key) return it.second }
            val base = baseHttpUrl
            val b = baseClient(base, key.pin)
            if (base != null) {
                b.addInterceptor(
                    serverInterceptor(base, key.httpOk, { token }, { saveToken(it) }) {
                        if (prefs.username.isBlank()) null
                        else doLogin(loginClient(base, key.pin, key.httpOk), baseUrl, prefs.username, prefs.password)
                    }
                )
            } else {
                b.addInterceptor { chain ->
                    if (!chain.request().url.isHttps) throw IOException("Blocked an unencrypted (http) connection")
                    chain.proceed(chain.request())
                }
            }
            val c = b.build()
            clientCache = key to c
            return c
        }

    fun url(path: String): String = baseUrl + (if (path.startsWith("/")) path else "/$path")

    fun reload() {
        baseUrl = prefs.baseUrl.trimEnd('/')
        baseHttpUrl = baseUrl.toHttpUrlOrNull()
        token = prefs.token
        clientCache = null
    }

    // ---------- auth ----------

    /**
     * Logs in against a candidate server. Returns the token ("" when the server has auth disabled).
     * [pin] is the confirmed certificate fingerprint for a self-signed server ("" = normal checks);
     * [httpOk] must be true (user confirmed) for a plain-http server.
     */
    suspend fun login(base: String, user: String, pass: String, pin: String, httpOk: Boolean): String =
        withContext(Dispatchers.IO) {
            val baseHttp = base.toHttpUrlOrNull() ?: throw Exception("That isn't a valid address")
            var tok = ""
            val client = baseClient(baseHttp, pin)
                .addInterceptor(serverInterceptor(baseHttp, httpOk, { tok }, { tok = it }, null))
                .build()
            if (user.isNotBlank()) {
                tok = doLogin(client, base, user, pass)
            }
            // Verify we can actually read the config with this token.
            client.newCall(Request.Builder().url("$base/api/config").build()).execute().use { r ->
                if (r.code == 401 || r.code == 403) throw AuthException("Server requires a login")
                if (!r.isSuccessful) throw Exception("Server answered HTTP ${r.code}")
                val body = r.body?.string().orEmpty()
                if (!body.trimStart().startsWith("{") || !body.contains("cameras"))
                    throw Exception("That doesn't look like a Frigate server")
            }
            tok
        }

    /** A client for (re-)logging in: no cookie, same pinning, redirect and cleartext rules. */
    private fun loginClient(base: HttpUrl, pin: String, httpOk: Boolean): OkHttpClient =
        baseClient(base, pin).addInterceptor(serverInterceptor(base, httpOk, { "" }, {}, null)).build()

    private fun doLogin(client: OkHttpClient, base: String, user: String, pass: String): String {
        val json = JSONObject().put("user", user).put("password", pass).toString()
        val req = Request.Builder()
            .url("$base/api/login")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 400 || r.code == 403) throw AuthException("Wrong username or password")
            if (!r.isSuccessful) throw Exception("Login failed: HTTP ${r.code}")
            return extractToken(r) ?: ""
        }
    }

    private fun extractToken(r: Response): String? =
        r.headers("Set-Cookie").firstNotNullOfOrNull { c ->
            if (c.startsWith("frigate_token=")) c.substringAfter("frigate_token=").substringBefore(';')
                .takeIf { it.isNotEmpty() } else null
        }

    private fun saveToken(t: String) {
        if (t != token) {
            token = t
            prefs.token = t
        }
    }

    /** Exact scheme + host + port match (ws/wss URLs are already normalised to http/https by OkHttp). */
    private fun sameOrigin(a: HttpUrl, b: HttpUrl) =
        a.scheme == b.scheme && a.host.equals(b.host, ignoreCase = true) && a.port == b.port

    /**
     * Application interceptor for every request to the server (an application interceptor, unlike
     * a network one, also runs for websocket handshakes). Automatic redirects are off, so each hop
     * comes back through here: only redirects to the configured server itself are followed, and
     * the token cookie is only ever attached to (and read back from) that server.
     */
    private fun serverInterceptor(
        base: HttpUrl,
        httpOk: Boolean,
        token: () -> String,
        onToken: (String) -> Unit,
        relogin: (() -> String?)?,
    ) = Interceptor { chain ->
        fun send(req: Request): Response {
            val same = sameOrigin(req.url, base)
            if (!req.url.isHttps && !(httpOk && same))
                throw IOException("Blocked an unencrypted (http) connection to ${req.url.host}")
            val t = token()
            val resp = chain.proceed(
                if (same && t.isNotEmpty()) req.newBuilder().header("Cookie", "frigate_token=$t").build()
                else req.newBuilder().removeHeader("Cookie").build()
            )
            if (same) extractToken(resp)?.let(onToken)
            return resp
        }

        fun sendFollowing(first: Request): Response {
            var req = first
            var resp = send(req)
            repeat(MAX_REDIRECTS) {
                if (resp.code !in REDIRECT_CODES) return resp
                val next = resp.header("Location")?.let { req.url.resolve(it) } ?: return resp
                if (!sameOrigin(next, base)) return resp
                val keepBody = resp.code == 307 || resp.code == 308
                val b = req.newBuilder().url(next)
                if (!keepBody && req.method != "GET" && req.method != "HEAD") {
                    b.method("GET", null).removeHeader("Content-Type").removeHeader("Content-Length")
                }
                resp.close()
                req = b.build()
                resp = send(req)
            }
            return resp
        }

        val original = chain.request()
        var resp = sendFollowing(original)
        if (relogin != null && resp.code == 401 && sameOrigin(original.url, base)) {
            val fresh = try { relogin() } catch (e: Exception) { null }
            if (fresh != null) {
                resp.close()
                onToken(fresh)
                resp = sendFollowing(original)
            }
        }
        resp
    }

    /** Timeouts and, when [pin] is set, certificate pinning for [base]. */
    private fun baseClient(base: HttpUrl?, pin: String): OkHttpClient.Builder {
        val b = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            // Redirects are followed by [serverInterceptor], only within the server.
            .followRedirects(false)
            .followSslRedirects(false)
        if (base != null && base.isHttps && pin.isNotBlank()) {
            val tm = Tls.PinnedTrustManager(pin, Tls.systemTrustManager())
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), null)
            b.sslSocketFactory(ctx.socketFactory, tm)
            b.hostnameVerifier(Tls.pinnedHostnameVerifier(pin, base.host))
        }
        return b
    }

    // ---------- requests ----------

    private suspend fun getString(path: String): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url(path)).build()).execute().use { r ->
                if (r.code == 401) throw AuthException("Session expired — log in again")
                if (!r.isSuccessful) throw Exception("HTTP ${r.code} for $path")
                r.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            // A changed pinned certificate gets a clear explanation instead of a TLS error string.
            throw Tls.pinMismatchMessage(e)?.let { Exception(it, e) } ?: e
        }
    }

    suspend fun bytes(pathOrUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        val u = if (pathOrUrl.startsWith("http")) pathOrUrl else url(pathOrUrl)
        try {
            http.newCall(Request.Builder().url(u).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
        } catch (_: Exception) { null }
    }

    /** Camera groups from the Frigate config (filled in by [cameras]). */
    @Volatile var groups: List<CameraGroup> = emptyList()
        private set

    /**
     * WebRTC ICE servers from go2rtc's config (used for two-way talk). None if go2rtc doesn't list any:
     * the phone then never contacts a third-party STUN server (it used to fall back to Google's).
     */
    @Volatile var iceServersJson: String = DEFAULT_ICE
        private set

    suspend fun cameras(): List<CameraInfo> {
        val root = JSONObject(getString("/api/config"))
        iceServersJson = root.optJSONObject("go2rtc")?.optJSONObject("webrtc")?.optJSONArray("ice_servers")
            ?.takeIf { it.length() > 0 }?.toString() ?: DEFAULT_ICE
        groups = root.optJSONObject("camera_groups")?.let { g ->
            g.keys().asSequence().mapNotNull { k ->
                val o = g.optJSONObject(k) ?: return@mapNotNull null
                val arr = o.optJSONArray("cameras") ?: return@mapNotNull null
                val cams = (0 until arr.length()).map { arr.getString(it) }
                CameraGroup(k, cams, o.optInt("order", 0))
            }.sortedWith(compareBy({ it.order }, { it.name })).toList()
        } ?: emptyList()
        val cams = root.optJSONObject("cameras") ?: return emptyList()
        val list = mutableListOf<CameraInfo>()
        for (name in cams.keys()) {
            val c = cams.getJSONObject(name)
            val live = c.optJSONObject("live")
            val streams = mutableListOf<StreamOption>()
            live?.optJSONObject("streams")?.let { s ->
                for (k in s.keys()) streams += StreamOption(k, s.optString(k))
            }
            if (streams.isEmpty()) {
                val sn = live?.optString("stream_name").orEmpty().ifBlank { name }
                streams += StreamOption("Main", sn)
            }
            val onvifHost = c.optJSONObject("onvif")?.optString("host").orEmpty()
            val ui = c.optJSONObject("ui")
            list += CameraInfo(
                name = name,
                enabled = c.optBoolean("enabled", true),
                streams = streams,
                hasOnvif = onvifHost.isNotBlank(),
                order = ui?.optInt("order", 0) ?: 0,
                hasAudioDetection = c.optJSONObject("audio")?.let {
                    it.optBoolean("enabled_in_config", false) || it.optBoolean("enabled", false)
                } ?: false,
                hasNotifications = c.optJSONObject("notifications")?.let {
                    it.optBoolean("enabled_in_config", false) || it.optBoolean("enabled", false)
                } ?: false,
            )
        }
        return list.sortedWith(compareBy({ it.order }, { it.name }))
    }

    suspend fun recordings(camera: String, after: Double, before: Double): List<RecordingSegment> {
        val arr = JSONArray(getString("/api/$camera/recordings?after=${after.toLong()}&before=${before.toLong() + 1}"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RecordingSegment(
                o.getDouble("start_time"), o.getDouble("end_time"),
                o.optInt("motion", 0), o.optInt("objects", 0),
                o.optDouble("duration", o.getDouble("end_time") - o.getDouble("start_time")),
            )
        }.sortedBy { it.start }
    }

    suspend fun reviews(camera: String, after: Double, before: Double): List<ReviewItem> {
        val arr = JSONArray(
            getString("/api/review?cameras=$camera&after=${after.toLong()}&before=${before.toLong() + 1}&limit=500")
        )
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val data = o.optJSONObject("data")
            val objs = data?.optJSONArray("objects")
            val labels = (0 until (objs?.length() ?: 0)).map { objs!!.getString(it).substringBefore('-') }.distinct()
            ReviewItem(
                id = o.optString("id"),
                camera = o.optString("camera"),
                start = o.getDouble("start_time"),
                end = if (o.isNull("end_time")) null else o.optDouble("end_time"),
                severity = o.optString("severity"),
                labels = labels,
                thumbPath = o.optString("thumb_path").takeIf { it.isNotBlank() }
                    ?.let { p -> if (p.startsWith("/media/frigate")) p.removePrefix("/media/frigate") else p },
                reviewed = o.optBoolean("has_been_reviewed", false),
            )
        }.sortedBy { it.start }
    }

    /** Mark review items reviewed (or not) for the logged-in user. */
    suspend fun setReviewed(ids: List<String>, reviewed: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        if (reviewed) {
            val body = JSONObject().put("ids", JSONArray(ids)).put("reviewed", true).toString()
            val req = Request.Builder().url(url("/api/reviews/viewed"))
                .post(body.toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { if (!it.isSuccessful) throw Exception("HTTP ${it.code}") }
        } else {
            for (id in ids) {
                val req = Request.Builder().url(url("/api/review/$id/viewed")).delete().build()
                http.newCall(req).execute().use { if (!it.isSuccessful) throw Exception("HTTP ${it.code}") }
            }
        }
    }

    /** Raw /api/stats (structure varies a little between Frigate versions, so it's parsed leniently). */
    suspend fun stats(): JSONObject = JSONObject(getString("/api/stats"))

    /** Per-camera recording storage: { camera: { usage (MB), bandwidth (MB/h), usage_percent } }. */
    suspend fun recordingStorage(): JSONObject? = try { JSONObject(getString("/api/recordings/storage")) } catch (_: Exception) { null }

    /** True when go2rtc reports a microphone backchannel ("audio, sendonly") for [stream]. */
    suspend fun supportsTwoWayTalk(stream: String): Boolean = try {
        val o = JSONObject(getString("/api/go2rtc/streams/" + java.net.URLEncoder.encode(stream, "UTF-8")))
        val producers = o.optJSONArray("producers")
        (0 until (producers?.length() ?: 0)).any { i ->
            val medias = producers!!.optJSONObject(i)?.optJSONArray("medias")
            (0 until (medias?.length() ?: 0)).any { j -> medias!!.optString(j).contains("audio, sendonly") }
        }
    } catch (_: Exception) { false }

    /** An MP4 of any recorded range (works on all Frigate versions this app supports). */
    fun clipUrl(camera: String, start: Double, end: Double) =
        url("/api/$camera/start/${"%.1f".format(java.util.Locale.US, start)}/end/${"%.1f".format(java.util.Locale.US, end)}/clip.mp4")

    suspend fun version(): String? = try { getString("/api/version").trim().trim('"') } catch (_: Exception) { null }

    suspend fun ptzInfo(camera: String): PtzInfo? = try {
        val o = JSONObject(getString("/api/$camera/ptz/info"))
        val f = o.optJSONArray("features")
        val p = o.optJSONArray("presets")
        PtzInfo(
            features = (0 until (f?.length() ?: 0)).map { f!!.getString(it) }.toSet(),
            presets = (0 until (p?.length() ?: 0)).map { p!!.getString(it) },
        )
    } catch (_: Exception) { null }

    // ---------- URLs ----------

    fun latestJpg(camera: String, height: Int) = url("/api/$camera/latest.jpg?h=$height")

    fun snapshotAt(camera: String, t: Double, height: Int) =
        url("/api/$camera/recordings/${"%.2f".format(java.util.Locale.US, t)}/snapshot.jpg?height=$height")

    fun vodUrl(camera: String, start: Long, end: Long) =
        url("/vod/$camera/start/$start/end/$end/index.m3u8")

    fun wsUrl(path: String): String =
        url(path).replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")

    private companion object {
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        const val DEFAULT_ICE = "[]"
    }
}
