package com.vknxworks.frigatectrl.notify

import android.content.Context
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.data.ReviewItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Asks Frigate for review items (alerts, and detections if chosen) that are new since the last check and posts
 * one notification for each. The background check and wake-up pushes both end up here, one at a time, and the
 * list of review items already notified keeps them from notifying twice.
 */
object AlertCheck {
    private val mutex = Mutex()

    /** Set when another check was asked for (a push) while one may be running. */
    @Volatile var again = false

    /** At most this many notifications per check, newest first; the rest are only marked as seen. */
    private const val MAX_POSTS = 8
    /** A check never looks further back than this, however long the phone was off. */
    private const val MAX_LOOKBACK_SEC = 6 * 3600L
    /** Overlap with the previous check, so a review item that started just before it isn't missed. */
    private const val OVERLAP_SEC = 300L

    enum class Outcome { Done, Off, Locked, Failed }

    suspend fun run(context: Context, hintId: String? = null): Outcome = mutex.withLock { check(context, hintId) }

    private suspend fun check(context: Context, hintId: String?): Outcome {
        App.init(context)
        val s = App.notify
        if (!s.enabled || !App.prefs.isConfigured) return Outcome.Off
        // The login is sealed with the app lock and FrigateCtrl is locked: wait until it's unlocked.
        if (App.prefs.loginSealedAway) return Outcome.Locked
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000
        val last = s.lastCheckMs / 1000
        val after = if (last <= 0) nowSec - OVERLAP_SEC else maxOf(last - OVERLAP_SEC, nowSec - MAX_LOOKBACK_SEC)
        val items = try {
            val list = App.api.unreviewedSince(after.toDouble()).toMutableList()
            // A push can name the review item: fetch it too, in case it isn't in the list yet.
            if (hintId != null && list.none { it.id == hintId }) App.api.review(hintId)?.let { if (!it.reviewed) list += it }
            list
        } catch (_: Exception) {
            return Outcome.Failed
        }
        val seen = s.seen()
        val wanted = items.asSequence()
            // Older ones only when they're still going on and have become an alert since (a detection turns
            // into an alert once a required zone or object is seen), so that change isn't missed.
            .filter { it.start >= after || (it.isAlert && (it.end == null || it.id in seen)) }
            .filter { it.isAlert || (s.includeDetections && it.severity == "detection") }
            .filter { it.camera !in s.mutedCameras }
            .filter { r -> r.labels.isEmpty() || r.labels.any { it !in s.mutedLabels } }
            .filter { r -> keyOf(r) !in seen && !(r.severity == "detection" && keyOf(r.copy(severity = "alert")) in seen) }
            .sortedByDescending { it.start }
            .toList()
        wanted.forEachIndexed { i, r ->
            if (i < MAX_POSTS) AlertNotifier.post(context, r, s)
            seen[keyOf(r)] = r.start.toLong()
        }
        s.saveSeen(seen, nowSec)
        s.markChecked(nowMs)
        return Outcome.Done
    }

    /** A detection that later becomes an alert notifies once more, as an alert (it replaces the first one). */
    private fun keyOf(r: ReviewItem) = if (r.isAlert) "${r.id}#a" else r.id

    /**
     * The review id a push carries, if any. Accepts Frigate's own frigate/reviews MQTT message
     * ({"type":"new","after":{"id":...}}), a flat {"id":...} or {"review_id":...}, or a bare id. Anything
     * else is fine too: a push is only a wake-up call, and nothing in it is ever shown.
     */
    fun hintFrom(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 64_000) return null
        val o = runCatching { JSONObject(t) }.getOrNull()
        val id = if (o != null) {
            o.optJSONObject("after")?.optString("id")?.takeIf { it.isNotBlank() }
                ?: o.optString("review_id").takeIf { it.isNotBlank() }
                ?: o.optString("id").takeIf { it.isNotBlank() }
        } else {
            t.takeIf { !it.contains(' ') }
        }
        // Review ids look like "1718000000.123456-abc123": only those characters are passed on to the server.
        return id?.takeIf { it.length <= 64 && it.all { c -> c.isLetterOrDigit() || c in ".-_" } }
    }
}
