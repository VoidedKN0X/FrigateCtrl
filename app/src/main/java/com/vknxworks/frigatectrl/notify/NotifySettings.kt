package com.vknxworks.frigatectrl.notify

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.UUID

/**
 * Alert notification settings (observable from Compose) and the little state the checks keep:
 * when the server was last asked and which review items were already notified.
 */
class NotifySettings(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("notify", Context.MODE_PRIVATE)
    private val state = context.applicationContext.getSharedPreferences("notify_state", Context.MODE_PRIVATE)

    private var _enabled by mutableStateOf(sp.getBoolean(K_ENABLED, false))
    /** Alert notifications on (off until you turn them on). */
    var enabled: Boolean
        get() = _enabled
        set(v) { _enabled = v; sp.edit().putBoolean(K_ENABLED, v).apply() }

    private var _push by mutableStateOf(sp.getBoolean(K_PUSH, false))
    /** Wake-up pushes through ntfy, on top of the background check. */
    var push: Boolean
        get() = _push
        set(v) { _push = v; sp.edit().putBoolean(K_PUSH, v).apply() }

    private var _detections by mutableStateOf(sp.getBoolean(K_DETECTIONS, false))
    /** Detections too, not only alerts. */
    var includeDetections: Boolean
        get() = _detections
        set(v) { _detections = v; sp.edit().putBoolean(K_DETECTIONS, v).apply() }

    private var _interval by mutableIntStateOf(sp.getInt(K_INTERVAL, 15))
    /** Minutes between background checks (Android allows 15 at the least). */
    var intervalMinutes: Int
        get() = _interval
        set(v) { _interval = v.coerceAtLeast(15); sp.edit().putInt(K_INTERVAL, _interval).apply() }

    private var _mutedCameras by mutableStateOf(sp.getStringSet(K_MUTED_CAMERAS, emptySet())!!.toSet())
    /** Cameras that never notify. Empty: every camera does, including ones added later. */
    var mutedCameras: Set<String>
        get() = _mutedCameras
        set(v) { _mutedCameras = v; sp.edit().putStringSet(K_MUTED_CAMERAS, v).apply() }

    private var _mutedLabels by mutableStateOf(sp.getStringSet(K_MUTED_LABELS, emptySet())!!.toSet())
    /** Labels (person, car, ...) that don't notify on their own. */
    var mutedLabels: Set<String>
        get() = _mutedLabels
        set(v) { _mutedLabels = v; sp.edit().putStringSet(K_MUTED_LABELS, v).apply() }

    private var _quiet by mutableStateOf(sp.getBoolean(K_QUIET, false))
    /** Quiet hours: notifications still arrive, but silently. */
    var quietHours: Boolean
        get() = _quiet
        set(v) { _quiet = v; sp.edit().putBoolean(K_QUIET, v).apply() }

    private var _quietFrom by mutableIntStateOf(sp.getInt(K_QUIET_FROM, 23))
    var quietFrom: Int
        get() = _quietFrom
        set(v) { _quietFrom = v.coerceIn(0, 23); sp.edit().putInt(K_QUIET_FROM, _quietFrom).apply() }

    private var _quietTo by mutableIntStateOf(sp.getInt(K_QUIET_TO, 7))
    var quietTo: Int
        get() = _quietTo
        set(v) { _quietTo = v.coerceIn(0, 23); sp.edit().putInt(K_QUIET_TO, _quietTo).apply() }

    /** Whether quiet hours are on and the current hour is inside them (the range may run past midnight). */
    fun inQuietHours(now: Calendar = Calendar.getInstance()): Boolean {
        if (!quietHours || quietFrom == quietTo) return false
        val h = now.get(Calendar.HOUR_OF_DAY)
        return if (quietFrom < quietTo) h in quietFrom until quietTo else h >= quietFrom || h < quietTo
    }

    // ---------------------------------------------------------------- check state

    private var _lastCheck by mutableStateOf(state.getLong(K_LAST_CHECK, 0L))
    /** Wall-clock milliseconds of the last successful check (0 = never). */
    val lastCheckMs: Long get() = _lastCheck

    fun markChecked(ms: Long) {
        _lastCheck = ms
        state.edit().putLong(K_LAST_CHECK, ms).apply()
    }

    /** Review item ids already notified, with their start time (seconds), so old ones can be dropped. */
    fun seen(): MutableMap<String, Long> =
        state.getString(K_SEEN, "").orEmpty().split(',').mapNotNull { e ->
            val i = e.lastIndexOf(':')
            if (i <= 0) null else e.substring(0, i) to (e.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
        }.toMap(mutableMapOf<String, Long>())

    fun saveSeen(seen: Map<String, Long>, nowSec: Long) {
        // Two days is far longer than any review item stays new; ids never contain ',' or ':' at the end.
        val kept = seen.filterValues { nowSec - it < 2 * 86_400 }.entries.sortedByDescending { it.value }.take(500)
        state.edit().putString(K_SEEN, kept.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }

    /**
     * A random secret in every notification's tap action. MainActivity is exported (it's the launcher), so
     * it only opens a camera's History from an intent that carries this.
     */
    val linkSecret: String
        get() = state.getString(K_SECRET, null) ?: UUID.randomUUID().toString().also { state.edit().putString(K_SECRET, it).apply() }

    /** Starts the checks fresh: only what happens from now on is notified. */
    fun resetCheckState() {
        _lastCheck = 0L
        state.edit().remove(K_LAST_CHECK).remove(K_SEEN).apply()
    }

    /** Logging out: everything goes, notifications are off. */
    fun clear() {
        sp.edit().clear().apply()
        state.edit().clear().apply()
        _enabled = false; _push = false; _detections = false; _interval = 15
        _mutedCameras = emptySet(); _mutedLabels = emptySet()
        _quiet = false; _quietFrom = 23; _quietTo = 7; _lastCheck = 0L
    }

    private companion object {
        const val K_ENABLED = "enabled"
        const val K_PUSH = "push"
        const val K_DETECTIONS = "detections"
        const val K_INTERVAL = "interval_minutes"
        const val K_MUTED_CAMERAS = "muted_cameras"
        const val K_MUTED_LABELS = "muted_labels"
        const val K_QUIET = "quiet"
        const val K_QUIET_FROM = "quiet_from"
        const val K_QUIET_TO = "quiet_to"
        const val K_LAST_CHECK = "last_check"
        const val K_SEEN = "seen"
        const val K_SECRET = "link_secret"
    }
}
