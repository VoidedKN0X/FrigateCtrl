package com.vknxworks.frigatectrl.data

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock preferences: when to lock again and hiding the app's content. The PIN itself lives in
 * [com.vknxworks.frigatectrl.data.lock.LockVault]. The PIN hash here is only the old (3.2.x) lock,
 * kept until it's upgraded on the first unlock.
 */
class LockSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    private var _pinSet by mutableStateOf(prefs.contains(KEY_PIN_HASH))
    /** An old-style PIN from FrigateCtrl 3.2.x that hasn't been upgraded yet. */
    val legacyPinSet: Boolean get() = _pinSet

    /**
     * How long the app may sit in the background before it locks again. 0 = immediately,
     * -1 = only when the app restarts. 30 s (no longer offered) becomes immediately.
     */
    private var _after by mutableIntStateOf(prefs.getInt(KEY_LOCK_AFTER, 0).let { if (it in 1..59) 0 else it })
    var lockAfterSeconds: Int
        get() = _after
        set(v) { _after = v; prefs.edit().putInt(KEY_LOCK_AFTER, v).apply() }

    init {
        // Before 3.3.4 one setting, "Hide content in screenshots & recents" (on unless turned off), did both. Only a
        // choice the user made themselves is kept: turned on, both new switches are on; otherwise both start off.
        if (prefs.contains(KEY_HIDE)) {
            val on = prefs.getBoolean(KEY_HIDE, false)
            val edit = prefs.edit().remove(KEY_HIDE)
            if (on) edit.putBoolean(KEY_HIDE_SCREENSHOTS, true).putBoolean(KEY_HIDE_RECENTS, true)
            edit.commit()
        }
    }

    /** FLAG_SECURE: screenshots and screen recordings show a blank screen. Off by default. */
    private var _hideShots by mutableStateOf(prefs.getBoolean(KEY_HIDE_SCREENSHOTS, false))
    var hideScreenshots: Boolean
        get() = _hideShots
        set(v) { _hideShots = v; prefs.edit().putBoolean(KEY_HIDE_SCREENSHOTS, v).apply() }

    /** The recent-apps view shows a blank card instead of the app. Off by default. */
    private var _hideRecents by mutableStateOf(prefs.getBoolean(KEY_HIDE_RECENTS, false))
    var hideRecents: Boolean
        get() = _hideRecents
        set(v) { _hideRecents = v; prefs.edit().putBoolean(KEY_HIDE_RECENTS, v).apply() }

    /** Lets the lock screen submit as soon as enough digits are entered. */
    val pinLength: Int get() = prefs.getInt(KEY_PIN_LENGTH, 0)

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_LENGTH).remove(KEY_PIN_HASH).remove(KEY_PIN_SALT)
            .remove(KEY_BIOMETRIC).remove(KEY_FAILED).remove(KEY_LOCKOUT_UNTIL)
            .remove(KEY_LOCKOUT_LEN).remove(KEY_LOCKOUT_ELAPSED).remove(KEY_LOCKOUT_BOOT)
            .apply()
        _pinSet = false
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.let(::unb64) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null)?.let(::unb64) ?: return false
        return MessageDigest.isEqual(expected, hash(pin, salt))
    }

    /** Wrong-PIN counter that survives restarts, so killing the app doesn't reset the lockout. */
    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(v) = prefs.edit().putInt(KEY_FAILED, v).apply()

    /**
     * Counts a wrong PIN (on the lock screen or when changing/turning off the PIN) and starts a
     * lockout after [MAX_TRIES]: 30 s, doubling for every further batch of wrong PINs, capped at 15 minutes.
     */
    fun recordWrongPin() {
        val fails = failedAttempts + 1
        val edit = prefs.edit().putInt(KEY_FAILED, fails)
        if (fails >= MAX_TRIES) {
            val wait = (30_000L shl ((fails - MAX_TRIES) / MAX_TRIES).coerceAtMost(5)).coerceAtMost(900_000L)
            edit.putLong(KEY_LOCKOUT_LEN, wait)
                .putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + wait)
                .putLong(KEY_LOCKOUT_ELAPSED, SystemClock.elapsedRealtime() + wait)
                .putInt(KEY_LOCKOUT_BOOT, bootCount())
        }
        // Written to disk before returning, so killing the app right after a wrong PIN can't lose it.
        edit.commit()
    }

    /**
     * Milliseconds left of the wrong-PIN lockout, measured only on the monotonic clock, so changing
     * the phone's date or time neither ends it early nor stretches it. After a reboot (when that clock
     * restarts) the full lockout counts again from boot: the time since boot is the only part of the
     * wait that is known for sure to have passed, so rebooting can't be used to skip it.
     */
    fun lockoutRemainingMs(): Long {
        val len = prefs.getLong(KEY_LOCKOUT_LEN, 0)
        if (len <= 0) {
            // Lockout saved by an older version (wall clock only).
            val legacy = (prefs.getLong(KEY_LOCKOUT_UNTIL, 0) - System.currentTimeMillis()).coerceIn(0, 900_000L)
            if (legacy <= 0 && prefs.contains(KEY_LOCKOUT_UNTIL)) prefs.edit().remove(KEY_LOCKOUT_UNTIL).apply()
            return legacy
        }
        val now = SystemClock.elapsedRealtime()
        val end = prefs.getLong(KEY_LOCKOUT_ELAPSED, 0)
        val savedBoot = prefs.getInt(KEY_LOCKOUT_BOOT, -1)
        val boot = bootCount()
        val sameBoot = if (boot >= 0 && savedBoot >= 0) boot == savedBoot else now >= end - len
        val left = (if (sameBoot) end - now else len - now).coerceIn(0, len)
        if (left <= 0) {
            // Over: forget it, so a later reboot doesn't start it again.
            prefs.edit().remove(KEY_LOCKOUT_LEN).remove(KEY_LOCKOUT_ELAPSED)
                .remove(KEY_LOCKOUT_BOOT).remove(KEY_LOCKOUT_UNTIL).apply()
        }
        return left
    }

    private fun bootCount(): Int = try {
        android.provider.Settings.Global.getInt(appContext.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    } catch (_: Exception) { -1 }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val MAX_TRIES = 5
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_LOCK_AFTER = "lock_after"
        const val KEY_HIDE = "hide_content"
        const val KEY_HIDE_SCREENSHOTS = "hide_screenshots"
        const val KEY_HIDE_RECENTS = "hide_recents"
        const val KEY_FAILED = "pin_failed"
        const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        const val KEY_LOCKOUT_LEN = "pin_lockout_len"
        const val KEY_LOCKOUT_ELAPSED = "pin_lockout_elapsed"
        const val KEY_LOCKOUT_BOOT = "pin_lockout_boot"
    }
}
