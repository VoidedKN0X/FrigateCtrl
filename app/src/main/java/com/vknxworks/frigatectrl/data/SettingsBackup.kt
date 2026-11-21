package com.vknxworks.frigatectrl.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * FrigateCtrl's own settings as a small JSON file, to move them to another phone or keep a copy.
 *
 * Only settings: the camera grid, live view, History and notification filters. Never in the file: the
 * server address, the login (user name, password, token), the app PIN, anything about the app lock, a
 * confirmed certificate, the "plain HTTP accepted" choice, the push address and its secret.
 *
 * Reading a file never turns off anything that protects you: it can switch "Hide in screenshots" and "Hide
 * in recent apps" on but not off, never touches the app lock, never turns notifications or push on or off,
 * never trusts a certificate and never allows plain HTTP.
 *
 * Values are an Int, a Boolean or a set of names, by key. Pure, so it can be tested.
 */
object SettingsBackup {
    const val APP = "FrigateCtrl"
    const val KIND = "settings"
    const val VERSION = 1
    /** A settings file is tiny; anything bigger isn't one. */
    const val MAX_BYTES = 256 * 1024

    const val GRID_COLUMNS = "grid_columns"
    const val THUMB_REFRESH = "thumb_refresh_seconds"
    const val SHOW_DISABLED = "show_disabled_cameras"
    const val PREFER_SUB = "prefer_sub_stream"
    const val START_MUTED = "start_muted"
    const val KEEP_AWAKE = "keep_screen_on"
    const val AUTO_PIP = "auto_picture_in_picture"
    const val TIMELINE_MINUTES = "timeline_minutes"
    const val TIMELINE_HAPTICS = "timeline_haptics"
    const val EVENT_PREROLL = "event_preroll_seconds"
    const val NOTIFY_DETECTIONS = "notify_detections"
    const val NOTIFY_INTERVAL = "notify_interval_minutes"
    const val QUIET_HOURS = "quiet_hours"
    const val QUIET_FROM = "quiet_from"
    const val QUIET_TO = "quiet_to"
    const val MUTED_CAMERAS = "muted_cameras"
    const val MUTED_LABELS = "muted_labels"
    const val HIDE_SCREENSHOTS = "hide_in_screenshots"
    const val HIDE_RECENTS = "hide_in_recent_apps"

    /** The values each number may have: exactly what the settings screens offer. */
    private val intChoices: Map<String, Set<Int>> = mapOf(
        GRID_COLUMNS to setOf(0, 1, 2, 3),
        THUMB_REFRESH to setOf(0, 2, 4, 10),
        TIMELINE_MINUTES to setOf(5, 15, 60, 360),
        EVENT_PREROLL to setOf(0, 3, 5, 10),
        NOTIFY_INTERVAL to setOf(15, 30, 60, 120, 360),
        QUIET_FROM to (0..23).toSet(),
        QUIET_TO to (0..23).toSet(),
    )
    private val boolKeys = setOf(
        SHOW_DISABLED, PREFER_SUB, START_MUTED, KEEP_AWAKE, AUTO_PIP, TIMELINE_HAPTICS, NOTIFY_DETECTIONS, QUIET_HOURS,
        HIDE_SCREENSHOTS, HIDE_RECENTS,
    )
    /** Switches a file may turn on but never off. */
    private val onlyOn = setOf(HIDE_SCREENSHOTS, HIDE_RECENTS)
    private val setKeys = setOf(MUTED_CAMERAS, MUTED_LABELS)
    private val name = Regex("[A-Za-z0-9_ -]{1,64}")

    /** The keys in the order the confirmation lists them. */
    val keys: List<String> = listOf(
        GRID_COLUMNS, THUMB_REFRESH, SHOW_DISABLED, PREFER_SUB, START_MUTED, AUTO_PIP, KEEP_AWAKE,
        TIMELINE_MINUTES, EVENT_PREROLL, TIMELINE_HAPTICS, NOTIFY_DETECTIONS, NOTIFY_INTERVAL,
        QUIET_HOURS, QUIET_FROM, QUIET_TO, MUTED_CAMERAS, MUTED_LABELS, HIDE_SCREENSHOTS, HIDE_RECENTS,
    )

    fun toJson(values: Map<String, Any>): String {
        val s = JSONObject()
        for (key in keys) {
            when (val v = values[key] ?: continue) {
                is Int -> s.put(key, v)
                is Boolean -> s.put(key, v)
                is Set<*> -> s.put(key, JSONArray(v.filterIsInstance<String>().sorted()))
            }
        }
        return JSONObject().put("app", APP).put("kind", KIND).put("version", VERSION).put("settings", s).toString(2)
    }

    /** Reads a settings file. Null when it isn't a FrigateCtrl settings file; unknown keys and odd values are skipped. */
    fun parse(text: String): Map<String, Any>? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optString("app") != APP || root.optString("kind") != KIND) return null
        val version = root.optInt("version", 0)
        if (version < 1 || version > VERSION) return null
        val s = root.optJSONObject("settings") ?: return null
        val out = LinkedHashMap<String, Any>()
        for (key in keys) {
            if (!s.has(key) || s.isNull(key)) continue
            val raw = s.opt(key)
            when {
                key in intChoices -> (raw as? Int)?.takeIf { it in intChoices.getValue(key) }?.let { out[key] = it }
                key in boolKeys -> (raw as? Boolean)?.let { out[key] = it }
                key in setKeys -> (raw as? JSONArray)?.let { arr ->
                    out[key] = (0 until minOf(arr.length(), 500)).mapNotNull { i -> (arr.opt(i) as? String)?.takeIf { name.matches(it) } }.toSet()
                }
            }
        }
        return out
    }

    /** The keys whose value in [incoming] would replace a different one in [current]. */
    fun changes(current: Map<String, Any>, incoming: Map<String, Any>): List<String> = keys.filter { key ->
        val new = incoming[key] ?: return@filter false
        val old = current[key]
        if (key in onlyOn) new == true && old != true else new != old
    }
}
