package com.vknxworks.frigatectrl.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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

    @Volatile private var clientCache: Pair<Boolean, OkHttpClient>? = null

    /** Shared client: adds the auth cookie, follows token refreshes and re-logs in on 401. */
    val http: OkHttpClient
        get() {
            val insecure = prefs.allowInsecureTls
            clientCache?.let { if (it.first == insecure) return it.second }
            val c = buildClient(insecure, withAuth = true)
            clientCache = insecure to c
            return c
        }

    fun url(path: String): String = baseUrl + (if (path.startsWith("/")) path else "/$path")

    fun reload() {
        baseUrl = prefs.baseUrl.trimEnd('/')
        token = prefs.token
        clientCache = null
    }

    // ---------- auth ----------

    /** Logs in against a candidate server. Returns the token ("" when the server has auth disabled). */
    suspend fun login(base: String, user: String, pass: String, insecure: Boolean): String =
        withContext(Dispatchers.IO) {
            val client = buildClient(insecure, withAuth = false)
            var tok = ""
            if (user.isNotBlank()) {
                tok = doLogin(client, base, user, pass)
            }
            // Verify we can actually read the config with this token.
            val req = Request.Builder().url("$base/api/config").apply {
                if (tok.isNotEmpty()) header("Cookie", "frigate_token=$tok")
            }.build()
            client.newCall(req).execute().use { r ->
                if (r.code == 401 || r.code == 403) throw AuthException("Server requires a login")
                if (!r.isSuccessful) throw Exception("Server answered HTTP ${r.code}")
                val body = r.body?.string().orEmpty()
                if (!body.trimStart().startsWith("{") || !body.contains("cameras"))
                    throw Exception("That doesn't look like a Frigate server")
            }
            tok
        }

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

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val sameHost = baseUrl.isNotEmpty() && original.url.toString().startsWith(baseUrl)
        fun withCookie(t: String) = if (sameHost && t.isNotEmpty())
            original.newBuilder().header("Cookie", "frigate_token=$t").build() else original

        var resp = chain.proceed(withCookie(token))
        extractToken(resp)?.let { saveToken(it) }

        if (sameHost && resp.code == 401 && prefs.username.isNotBlank()) {
            resp.close()
            val fresh = try {
                doLogin(buildClient(prefs.allowInsecureTls, false), baseUrl, prefs.username, prefs.password)
            } catch (e: Exception) { null }
            if (fresh != null) {
                saveToken(fresh)
                resp = chain.proceed(withCookie(fresh))
            } else {
                resp = chain.proceed(withCookie(token))
            }
        }
        resp
    }

    private fun buildClient(insecure: Boolean, withAuth: Boolean): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
        if (insecure) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), SecureRandom())
            b.sslSocketFactory(ctx.socketFactory, tm)
            b.hostnameVerifier { _, _ -> true }
        }
        if (withAuth) b.addInterceptor(authInterceptor)
        return b.build()
    }

    // ---------- requests ----------

    private suspend fun getString(path: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url(path)).build()).execute().use { r ->
            if (r.code == 401) throw AuthException("Session expired — log in again")
            if (!r.isSuccessful) throw Exception("HTTP ${r.code} for $path")
            r.body?.string().orEmpty()
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

    /** WebRTC ICE servers from go2rtc's config (used for two-way talk); public STUN if none set. */
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
        const val DEFAULT_ICE = "[{\"urls\":\"stun:stun.l.google.com:19302\"}]"
    }
}
