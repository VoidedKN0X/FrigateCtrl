package com.vknxworks.frigatectrl.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vknxworks.frigatectrl.data.lock.LockVault
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Saved server settings. Password and token are encrypted with an Android Keystore key. */
class Prefs(context: Context, private val vault: LockVault) {
    private val sp = context.getSharedPreferences("frigate_remote", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("base_url", "") ?: ""
        set(v) = sp.edit().putString("base_url", v).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit().putString("username", v).apply()

    // With the app lock on, the password and token are also encrypted with the lock's key, so they
    // can't be read while the app is locked.
    var password: String
        get() = open(decrypt(sp.getString("password", null)))
        set(v) { seal(v)?.let { sp.edit().putString("password", encrypt(it)).apply() } }

    var token: String
        get() = open(decrypt(sp.getString("token", null)))
        set(v) { seal(v)?.let { sp.edit().putString("token", encrypt(it)).apply() } }

    private fun open(stored: String): String =
        if (LockVault.isSealed(stored)) vault.open(stored).orEmpty() else stored

    /** Null while locked: nothing is saved then (the old value stays). */
    private fun seal(v: String): String? = when {
        v.isEmpty() || !vault.enabled -> v
        else -> vault.seal(v)
    }

    /** The app lock was just turned on (unlocked): encrypt the saved login with its key too. */
    fun sealCredentials() {
        val p = password
        val t = token
        password = p
        token = t
    }

    /** Turning the app lock off (unlocked): [disable] forgets the lock's key, then the login is saved without it. */
    fun unsealCredentials(disable: () -> Unit) {
        val p = password
        val t = token
        disable()
        password = p
        token = t
    }

    var allowInsecureTls: Boolean
        get() = sp.getBoolean("insecure_tls", false)
        set(v) = sp.edit().putBoolean("insecure_tls", v).apply()

    /**
     * SHA-256 fingerprint of the self-signed certificate the user confirmed (trust on first use).
     * Empty while [allowInsecureTls] is on means an older install that trusted every certificate:
     * the app asks the user to confirm the fingerprint before connecting again.
     */
    var certPin: String
        get() = sp.getString("cert_pin", "") ?: ""
        set(v) = sp.edit().putString("cert_pin", v).apply()

    /** The user confirmed that the configured server is plain http (not encrypted). */
    var httpConfirmed: Boolean
        get() = sp.getBoolean("http_confirmed", false)
        set(v) = sp.edit().putBoolean("http_confirmed", v).apply()

    val isHttp: Boolean get() = baseUrl.startsWith("http://", ignoreCase = true)

    /** Self-signed mode from an older version that has no pinned certificate yet. */
    val needsCertPin: Boolean get() = allowInsecureTls && certPin.isBlank() && baseUrl.startsWith("https://", ignoreCase = true)

    var lastCamera: String
        get() = sp.getString("last_camera", "") ?: ""
        set(v) = sp.edit().putString("last_camera", v).apply()

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    fun clear() = sp.edit().clear().apply()

    // --- Keystore AES-GCM ---
    private val alias = "frigate_remote_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val out = c.iv + c.doFinal(plain.toByteArray())
            Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    private fun decrypt(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12))
        } catch (_: Exception) { "" }
    }
}
