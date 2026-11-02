package me.ripps.xlauthenticator

import java.net.URI
import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238 TOTP with the parameters Square Enix uses: HMAC-SHA1, 30 s steps, 6 digits. */
object Totp {
    const val PERIOD_SECONDS = 30L
    private const val DIGITS = 6

    fun code(secret: ByteArray, unixSeconds: Long = System.currentTimeMillis() / 1000): String {
        val counter = unixSeconds / PERIOD_SECONDS
        val message = ByteArray(8) { i -> (counter ushr (56 - 8 * i)).toByte() }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val hash = mac.doFinal(message)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    fun secondsRemaining(unixSeconds: Long = System.currentTimeMillis() / 1000): Long =
        PERIOD_SECONDS - unixSeconds % PERIOD_SECONDS

    /**
     * Accepts either an otpauth:// URI or a bare Base32 key, with any spacing or case.
     * Returns null when the input isn't a usable key.
     */
    fun parseSecret(input: String): ByteArray? {
        val trimmed = input.trim()
        val base32 = if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            val query = runCatching { URI(trimmed).rawQuery }.getOrNull() ?: return null
            query.split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.size == 2 && it[0].equals("secret", ignoreCase = true) }
                ?.let { URLDecoder.decode(it[1], "UTF-8") }
                ?: return null
        } else {
            trimmed
        }
        return base32Decode(base32)?.takeIf { it.size >= 10 }
    }

    fun base32Decode(input: String): ByteArray? {
        val clean = input.uppercase().filterNot { it.isWhitespace() || it == '-' }.trimEnd('=')
        if (clean.isEmpty()) return null
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add((buffer shr bits).toByte())
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
}
