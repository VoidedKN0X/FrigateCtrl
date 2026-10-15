package com.vknxworks.frigateremote.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Saved server settings. Password and token are encrypted with an Android Keystore key. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("frigate_remote", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("base_url", "") ?: ""
        set(v) = sp.edit().putString("base_url", v).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit().putString("username", v).apply()

    var password: String
        get() = decrypt(sp.getString("password", null))
        set(v) = sp.edit().putString("password", encrypt(v)).apply()

    var token: String
        get() = decrypt(sp.getString("token", null))
        set(v) = sp.edit().putString("token", encrypt(v)).apply()

    var allowInsecureTls: Boolean
        get() = sp.getBoolean("insecure_tls", false)
        set(v) = sp.edit().putBoolean("insecure_tls", v).apply()

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
