package me.ripps.xlauthenticator

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TotpTest {
    private val rfcSecret = "12345678901234567890".toByteArray()

    /** RFC 6238 appendix B SHA-1 vectors, truncated to the last 6 digits. */
    @Test
    fun rfc6238Vectors() {
        mapOf(
            59L to "287082",
            1111111109L to "081804",
            1111111111L to "050471",
            1234567890L to "005924",
            2000000000L to "279037",
            20000000000L to "353130",
        ).forEach { (time, expected) -> assertEquals("t=$time", expected, Totp.code(rfcSecret, time)) }
    }

    @Test
    fun parsesBareBase32WithSpacingAndCase() {
        assertArrayEquals(rfcSecret, Totp.parseSecret(" gezd gnbv gy3t qojq gezd gnbv gy3t qojq "))
    }

    @Test
    fun parsesOtpauthUri() {
        val uri = "otpauth://totp/Square%20Enix:me?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Square%20Enix"
        assertArrayEquals(rfcSecret, Totp.parseSecret(uri))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Totp.parseSecret("not a key!"))
        assertNull(Totp.parseSecret(""))
        assertNull(Totp.parseSecret("otpauth://totp/x?issuer=y"))
    }

    @Test
    fun secondsRemaining() {
        assertEquals(30, Totp.secondsRemaining(60))
        assertEquals(1, Totp.secondsRemaining(89))
    }
}
