package com.vknxworks.frigatectrl.data

import android.content.Context
import androidx.annotation.StringRes
import com.vknxworks.frigatectrl.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The theme choices every Ctrl app offers. Stored by position, so new ones go at the end. */
enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark), Black(R.string.theme_black),
}

/** User preferences, backed by SharedPreferences and observable from Compose. */
class Settings(context: Context) {
    private val sp = context.getSharedPreferences("frigate_settings", Context.MODE_PRIVATE)

    init {
        // FrigateCtrl 3.2.11 removed the optional anonymous statistics. Delete what they stored
        // (the on/off switch and whether the question was answered) so nothing of them stays on the phone.
        val leftover = sp.all.keys.filter { it.startsWith("usage") }
        if (leftover.isNotEmpty()) {
            val editor = sp.edit()
            leftover.forEach { editor.remove(it) }
            editor.apply()
        }
        // Before 3.3.2, pure black was a separate switch: Dark with it on is now the Pure black theme.
        if (sp.contains("amoled")) {
            val edit = sp.edit().remove("amoled")
            if (sp.getBoolean("amoled", false) && sp.getInt("theme", 0) == ThemeMode.Dark.ordinal) edit.putInt("theme", ThemeMode.Black.ordinal)
            edit.apply()
        }
    }

    private fun bool(key: String, def: Boolean) = mutableStateOf(sp.getBoolean(key, def))
    private fun int(key: String, def: Int) = mutableIntStateOf(sp.getInt(key, def))

    // Appearance
    private val _theme = mutableStateOf(ThemeMode.entries[sp.getInt("theme", 0).coerceIn(0, ThemeMode.entries.size - 1)])
    var theme: ThemeMode
        get() = _theme.value
        set(v) { _theme.value = v; sp.edit().putInt("theme", v.ordinal).apply() }

    private val _dynamic = bool("dynamic_color", true)
    var dynamicColor: Boolean
        get() = _dynamic.value
        set(v) { _dynamic.value = v; sp.edit().putBoolean("dynamic_color", v).apply() }

    /** True black backgrounds (the "Pure black" theme). */
    val pureBlack: Boolean get() = theme == ThemeMode.Black

    // Cameras
    /** 0 = automatic, otherwise a fixed number of columns. */
    private val _cols = int("grid_columns", 0)
    var gridColumns: Int
        get() = _cols.intValue
        set(v) { _cols.intValue = v; sp.edit().putInt("grid_columns", v).apply() }

    /** Seconds between thumbnail refreshes; 0 = never. */
    private val _thumb = int("thumb_refresh", 4)
    var thumbRefreshSec: Int
        get() = _thumb.intValue
        set(v) { _thumb.intValue = v; sp.edit().putInt("thumb_refresh", v).apply() }

    private val _showDisabled = bool("show_disabled", true)
    var showDisabled: Boolean
        get() = _showDisabled.value
        set(v) { _showDisabled.value = v; sp.edit().putBoolean("show_disabled", v).apply() }

    // Live
    private val _preferSub = bool("prefer_sub", false)
    var preferSubStream: Boolean
        get() = _preferSub.value
        set(v) { _preferSub.value = v; sp.edit().putBoolean("prefer_sub", v).apply() }

    private val _muted = bool("start_muted", true)
    var startMuted: Boolean
        get() = _muted.value
        set(v) { _muted.value = v; sp.edit().putBoolean("start_muted", v).apply() }

    private val _awake = bool("keep_awake", true)
    var keepScreenOn: Boolean
        get() = _awake.value
        set(v) { _awake.value = v; sp.edit().putBoolean("keep_awake", v).apply() }

    private val _pip = bool("auto_pip", true)
    var autoPip: Boolean
        get() = _pip.value
        set(v) { _pip.value = v; sp.edit().putBoolean("auto_pip", v).apply() }

    // History
    /** Minutes visible on the timeline when history opens. */
    private val _zoom = int("timeline_minutes", 15)
    var timelineMinutes: Int
        get() = _zoom.intValue
        set(v) { _zoom.intValue = v; sp.edit().putInt("timeline_minutes", v).apply() }

    private val _haptics = bool("timeline_haptics", true)
    var timelineHaptics: Boolean
        get() = _haptics.value
        set(v) { _haptics.value = v; sp.edit().putBoolean("timeline_haptics", v).apply() }

    /** Seconds before an event's start that playback begins when jumping to it. */
    private val _preroll = int("event_preroll", 3)
    var eventPreroll: Int
        get() = _preroll.intValue
        set(v) { _preroll.intValue = v; sp.edit().putInt("event_preroll", v).apply() }
}
