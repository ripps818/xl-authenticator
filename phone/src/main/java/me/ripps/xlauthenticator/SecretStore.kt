package me.ripps.xlauthenticator

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the TOTP secret encrypted with an AES-GCM key that never leaves the Android Keystore,
 * plus the plain settings (PC IPs, auto-prompt toggle).
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("xl", Context.MODE_PRIVATE)

    var ips: List<String>
        get() = parseIps(prefs.getString(KEY_IPS, "") ?: "")
        set(value) = prefs.edit().putString(KEY_IPS, value.joinToString(";")).apply()

    var autoPrompt: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PROMPT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PROMPT, value).apply()

    val hasSecret: Boolean get() = prefs.contains(KEY_SECRET)

    fun secret(): ByteArray? {
        val stored = prefs.getString(KEY_SECRET, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_LENGTH))
            cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH)
        }.getOrNull()
    }

    fun setSecret(secret: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val stored = cipher.iv + cipher.doFinal(secret)
        prefs.edit().putString(KEY_SECRET, Base64.encodeToString(stored, Base64.NO_WRAP)).apply()
    }

    fun clearSecret() = prefs.edit().remove(KEY_SECRET).apply()

    /** The current code, or null when no secret is set. */
    fun currentCode(): String? = secret()?.let { Totp.code(it) }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "totp-secret"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val KEY_SECRET = "secret"
        private const val KEY_IPS = "ips"
        private const val KEY_AUTO_PROMPT = "auto_prompt"

        fun parseIps(text: String): List<String> =
            text.split(';', ',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
