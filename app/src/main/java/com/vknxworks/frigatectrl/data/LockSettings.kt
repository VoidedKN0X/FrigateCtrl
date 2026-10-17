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
 * App lock preferences. Only a salted PBKDF2 hash of the PIN is stored, never the PIN itself.
 * Same design as CraftyCtrl's lock.
 */
class LockSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    private var _pinSet by mutableStateOf(prefs.contains(KEY_PIN_HASH))
    val pinSet: Boolean get() = _pinSet

    private var _bio by mutableStateOf(prefs.getBoolean(KEY_BIOMETRIC, false))
    var biometricEnabled: Boolean
        get() = _pinSet && _bio
        set(v) { _bio = v; prefs.edit().putBoolean(KEY_BIOMETRIC, v).apply() }

    /** How long the app may sit in the background before it locks again. 0 = immediately. */
    private var _after by mutableIntStateOf(prefs.getInt(KEY_LOCK_AFTER, 0))
    var lockAfterSeconds: Int
        get() = _after
        set(v) { _after = v; prefs.edit().putInt(KEY_LOCK_AFTER, v).apply() }

    /** Hide the app in the recent-apps screen and block screenshots/screen recording (works with or without a PIN). */
    private var _hide by mutableStateOf(prefs.getBoolean(KEY_HIDE, true))
    var hideContent: Boolean
        get() = _hide
        set(v) { _hide = v; prefs.edit().putBoolean(KEY_HIDE, v).apply() }

    /** Lets the lock screen submit as soon as enough digits are entered. */
    val pinLength: Int get() = prefs.getInt(KEY_PIN_LENGTH, 0)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, b64(salt))
            .putString(KEY_PIN_HASH, b64(hash(pin, salt)))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .putInt(KEY_FAILED, 0)
            .apply()
        _pinSet = true
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_LENGTH).remove(KEY_PIN_HASH).remove(KEY_PIN_SALT)
            .remove(KEY_BIOMETRIC).remove(KEY_FAILED).remove(KEY_LOCKOUT_UNTIL)
            .remove(KEY_LOCKOUT_LEN).remove(KEY_LOCKOUT_ELAPSED).remove(KEY_LOCKOUT_BOOT)
            .apply()
        _pinSet = false
        _bio = false
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
        failedAttempts = fails
        if (fails >= MAX_TRIES) {
            val wait = (30_000L shl ((fails - MAX_TRIES) / MAX_TRIES).coerceAtMost(5)).coerceAtMost(900_000L)
            prefs.edit()
                .putLong(KEY_LOCKOUT_LEN, wait)
                .putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + wait)
                .putLong(KEY_LOCKOUT_ELAPSED, SystemClock.elapsedRealtime() + wait)
                .putInt(KEY_LOCKOUT_BOOT, bootCount())
                .apply()
        }
    }

    /**
     * Milliseconds left of the wrong-PIN lockout. Measured on the monotonic clock, so changing the
     * phone's date or time neither ends it early nor stretches it; after a reboot (when that clock
     * restarts) the wall clock is used, never for longer than the lockout itself.
     */
    fun lockoutRemainingMs(): Long {
        val len = prefs.getLong(KEY_LOCKOUT_LEN, 0)
        if (len <= 0) {
            // Lockout saved by an older version (wall clock only).
            return (prefs.getLong(KEY_LOCKOUT_UNTIL, 0) - System.currentTimeMillis()).coerceIn(0, 900_000L)
        }
        val boot = bootCount()
        val left = if (boot >= 0 && boot == prefs.getInt(KEY_LOCKOUT_BOOT, -2)) {
            prefs.getLong(KEY_LOCKOUT_ELAPSED, 0) - SystemClock.elapsedRealtime()
        } else {
            prefs.getLong(KEY_LOCKOUT_UNTIL, 0) - System.currentTimeMillis()
        }
        return left.coerceIn(0, len)
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
        const val KEY_FAILED = "pin_failed"
        const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        const val KEY_LOCKOUT_LEN = "pin_lockout_len"
        const val KEY_LOCKOUT_ELAPSED = "pin_lockout_elapsed"
        const val KEY_LOCKOUT_BOOT = "pin_lockout_boot"
    }
}
