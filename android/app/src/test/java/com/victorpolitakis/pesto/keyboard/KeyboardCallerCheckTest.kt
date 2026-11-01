package com.victorpolitakis.pesto.keyboard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only the app signed with the keyboard's key may dictate, by certificate, never by name. */
class KeyboardCallerCheckTest {
    private val keyboardUid = 10_123
    private val otherUid = 10_456
    private val keyboardCert = ByteArray(32) { it.toByte() }
    private val otherCert = ByteArray(32) { (it + 100).toByte() }

    /** The PackageManager of the test: which uid is signed with which certificate. */
    private val signed = SigningCertificates { uid, sha256 ->
        (uid == keyboardUid && sha256.contentEquals(keyboardCert)) || (uid == otherUid && sha256.contentEquals(otherCert))
    }

    private fun check(vararg trusted: ByteArray) = KeyboardCallerCheck({ trusted.toList() }, signed)

    @Test
    fun `the app signed with the trusted certificate is allowed`() {
        assertTrue(check(keyboardCert).isAllowed(keyboardUid))
    }

    @Test
    fun `an app signed with another certificate is refused`() {
        assertFalse(check(keyboardCert).isAllowed(otherUid))
    }

    @Test
    fun `without a trusted certificate nobody is allowed`() {
        assertFalse(check().isAllowed(keyboardUid))
        assertFalse(check().isAllowed(otherUid))
    }

    @Test
    fun `any of several trusted certificates is enough`() {
        val both = check(otherCert, keyboardCert)
        assertTrue(both.isAllowed(keyboardUid))
        assertTrue(both.isAllowed(otherUid))
    }

    @Test
    fun `digests parse as apksigner prints them`() {
        val hex = keyboardCert.joinToString("") { "%02x".format(it) }
        val colons = keyboardCert.joinToString(":") { "%02X".format(it) }
        assertArrayEquals(keyboardCert, KeyboardCallerCheck.parseSha256s(hex).single())
        assertArrayEquals(keyboardCert, KeyboardCallerCheck.parseSha256s(colons).single())
        assertEquals(2, KeyboardCallerCheck.parseSha256s("$hex, $colons").size)
    }

    @Test
    fun `anything but 32 bytes of hex is dropped`() {
        assertTrue(KeyboardCallerCheck.parseSha256s("").isEmpty())
        assertTrue(KeyboardCallerCheck.parseSha256s("abcd").isEmpty())
        assertTrue(KeyboardCallerCheck.parseSha256s("z".repeat(64)).isEmpty())
    }
}
