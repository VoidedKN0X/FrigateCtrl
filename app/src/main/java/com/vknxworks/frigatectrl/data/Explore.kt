package com.vknxworks.frigatectrl.data

import org.json.JSONArray
import org.json.JSONObject

/** A tracked object (Frigate calls it an event): one person, car, ... followed from when it appears until it leaves. */
data class TrackedObject(
    val id: String,
    val camera: String,
    val label: String,
    val subLabel: String?,
    val start: Double,
    val end: Double?,
    /** Highest confidence while it was tracked, 0..1, when Frigate reports it. */
    val score: Double?,
    val zones: List<String>,
    val hasClip: Boolean,
    val hasSnapshot: Boolean,
    /** Description written by Frigate's generative AI, when that is set up. */
    val description: String?,
)

/** What Explore is filtered on. Empty sets mean "all". */
data class ExploreFilter(
    val cameras: Set<String> = emptySet(),
    val labels: Set<String> = emptySet(),
    /** Only objects that started within this many hours; 0 = any time. */
    val lastHours: Int = 24,
    /** Free text; only used when the server has semantic search on. */
    val query: String = "",
)

/** Building Explore's requests and reading their answers. Pure, so it can be tested. */
object ExploreRequests {
    const val PAGE = 50

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun common(f: ExploreFilter, nowSec: Double, before: Double?): String = buildString {
        append("cameras=").append(if (f.cameras.isEmpty()) "all" else f.cameras.sorted().joinToString(",") { enc(it) })
        append("&labels=").append(if (f.labels.isEmpty()) "all" else f.labels.sorted().joinToString(",") { enc(it) })
        if (f.lastHours > 0) append("&after=").append((nowSec - f.lastHours * 3600L).toLong())
        if (before != null) append("&before=").append("%.3f".format(java.util.Locale.US, before))
        append("&limit=").append(PAGE)
    }

    /**
     * The list of tracked objects, newest first. [before] continues the list from an earlier page
     * (the start time of the last object shown).
     */
    fun list(f: ExploreFilter, nowSec: Double, before: Double? = null): String =
        "/api/events?" + common(f, nowSec, before) + "&sort=date_desc&include_thumbnails=0"

    /** A semantic search for [ExploreFilter.query] in thumbnails and descriptions (needs semantic search on the server). */
    fun search(f: ExploreFilter, nowSec: Double): String =
        "/api/events/search?query=" + enc(f.query.trim()) + "&search_type=thumbnail,description&" +
            common(f, nowSec, null) + "&include_thumbnails=0"

    fun parse(text: String): List<TrackedObject> {
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseOne) }
    }

    private fun parseOne(o: JSONObject): TrackedObject? {
        val id = o.optString("id").takeIf { idOk(it) } ?: return null
        val camera = o.optString("camera").takeIf { it.isNotBlank() } ?: return null
        val data = o.optJSONObject("data")
        val zones = o.optJSONArray("zones")
        fun text(obj: JSONObject?, key: String): String? =
            if (obj == null || obj.isNull(key)) null else obj.optString(key).takeIf { it.isNotBlank() }
        // Before 0.17 the score sat on the event itself; since then it's in "data".
        val score = listOf(data?.optDouble("top_score", Double.NaN), o.optDouble("top_score", Double.NaN), data?.optDouble("score", Double.NaN))
            .firstOrNull { it != null && !it.isNaN() }
        return TrackedObject(
            id = id,
            camera = camera,
            label = o.optString("label").ifBlank { "object" },
            // A recognised face or plate comes as "name" or as ["name", score].
            subLabel = (o.optJSONArray("sub_label")?.optString(0) ?: text(o, "sub_label"))?.takeIf { it.isNotBlank() },
            start = o.optDouble("start_time", 0.0),
            end = if (o.isNull("end_time")) null else o.optDouble("end_time").takeIf { !it.isNaN() },
            score = score,
            zones = (0 until (zones?.length() ?: 0)).mapNotNull { zones?.optString(it)?.takeIf { z -> z.isNotBlank() } },
            hasClip = o.optBoolean("has_clip", false),
            hasSnapshot = o.optBoolean("has_snapshot", false),
            description = text(data, "description"),
        )
    }

    /** Event ids look like "1718000000.123456-abc123": only those characters are ever put in a request path. */
    fun idOk(id: String): Boolean = id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it in ".-_" }
}

/** One of the server's profiles (Frigate 0.18+): a named set of settings that can be switched on as a whole. */
data class FrigateProfile(val name: String, val friendlyName: String)

data class ProfileState(val profiles: List<FrigateProfile>, val active: String?)

/** One place where motion search (Frigate 0.18+) found a change inside the chosen area. */
data class MotionHit(val time: Double, val changePercent: Double)

/** A motion search job as Frigate reports it. */
data class MotionSearchStatus(
    /** queued, running, success, failed or cancelled. */
    val status: String,
    val hits: List<MotionHit>,
    /** 0..1 while it runs, when Frigate says. */
    val progress: Double?,
    val message: String?,
) {
    val running: Boolean get() = status == "queued" || status == "running"
}

/** Requests and answers of Frigate 0.18's profiles and motion search. Pure, so it can be tested. */
object Frigate018 {
    /** Profiles and motion search exist from this version on; older servers never see them in the app. */
    val SINCE = FrigateVersion(0, 18, 0)

    fun supported(version: FrigateVersion?): Boolean = version != null && version >= SINCE

    fun parseProfiles(text: String): ProfileState {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return ProfileState(emptyList(), null)
        val arr = o.optJSONArray("profiles")
        val list = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val p = arr?.optJSONObject(i) ?: return@mapNotNull null
            val name = p.optString("name").takeIf { profileNameOk(it) } ?: return@mapNotNull null
            FrigateProfile(name, p.optString("friendly_name").ifBlank { name })
        }
        val active = if (o.isNull("active_profile")) null else o.optString("active_profile").takeIf { it.isNotBlank() && it != "none" }
        return ProfileState(list, active)
    }

    /** Profile names are keys from the config file; only plain ones are sent back to the server. */
    fun profileNameOk(name: String): Boolean = name.isNotEmpty() && name.length <= 64 && name.all { it.isLetterOrDigit() || it in "_-" }

    /**
     * The body that starts a motion search in the rectangle [left]..[right] by [top]..[bottom]
     * (fractions of the picture, 0..1), as the four corners of a polygon.
     */
    fun motionSearchBody(start: Double, end: Double, left: Float, top: Float, right: Float, bottom: Float): JSONObject {
        val l = minOf(left, right).coerceIn(0f, 1f).toDouble()
        val r = maxOf(left, right).coerceIn(0f, 1f).toDouble()
        val t = minOf(top, bottom).coerceIn(0f, 1f).toDouble()
        val b = maxOf(top, bottom).coerceIn(0f, 1f).toDouble()
        val points = JSONArray()
        for ((x, y) in listOf(l to t, r to t, r to b, l to b)) points.put(JSONArray().put(x).put(y))
        return JSONObject()
            .put("start_time", start)
            .put("end_time", end)
            .put("polygon_points", points)
            .put("max_results", 50)
    }

    fun parseMotionStatus(text: String): MotionSearchStatus? {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val status = o.optString("status").takeIf { it.isNotBlank() } ?: return null
        val arr = o.optJSONArray("results")
        val hits = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val h = arr?.optJSONObject(i) ?: return@mapNotNull null
            val time = h.optDouble("timestamp", Double.NaN).takeIf { !it.isNaN() && it > 0 } ?: return@mapNotNull null
            MotionHit(time, h.optDouble("change_percentage", 0.0))
        }.sortedBy { it.time }
        return MotionSearchStatus(
            status = status,
            hits = hits,
            progress = if (o.isNull("progress")) null else o.optDouble("progress", Double.NaN).takeIf { !it.isNaN() },
            message = (if (o.isNull("error_message")) null else o.optString("error_message").takeIf { it.isNotBlank() })
                ?: o.optString("message").takeIf { it.isNotBlank() },
        )
    }

    /** Job ids are made by the server; only plain ones are put back in a request path. */
    fun jobIdOk(id: String): Boolean = id.isNotEmpty() && id.length <= 80 && id.all { it.isLetterOrDigit() || it in "_-." }
}

/** When the motion search screen stops waiting for Frigate's answer. */
object MotionPoll {
    /** Status answers in a row that couldn't be read (not JSON, no status). About 15 s at one every 1.5 s. */
    const val MAX_UNREADABLE = 10
    /** A search that is still running after this long is given up. */
    const val MAX_MS = 30 * 60_000L

    fun giveUp(unreadableInARow: Int, elapsedMs: Long): Boolean = unreadableInARow >= MAX_UNREADABLE || elapsedMs >= MAX_MS
}
