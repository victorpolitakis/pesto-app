package com.victorpolitakis.pesto.keyboard

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The local signing key: made only when signing, read-only otherwise, and stable once made. */
@RunWith(AndroidJUnit4::class)
class LocalSigningKeyTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun newKey() = LocalSigningKey(context)

    @Test
    fun `reading does not make the key`() {
        val key = newKey()
        assertNull(key.certificateSha256OrNull())
        assertNull(key.certificateSha256Bytes())
    }

    @Test
    fun `signer makes the key, and then it can be read`() {
        val key = newKey()
        key.signer()
        val hex = key.certificateSha256OrNull()
        assertNotNull(hex)
        assertEquals(64, hex!!.length)
        assertEquals(32, key.certificateSha256Bytes()!!.size)
    }

    @Test
    fun `the key is stable across instances, since it is on disk`() {
        newKey().signer()
        val first = newKey().certificateSha256OrNull()
        val second = newKey().certificateSha256OrNull()
        assertEquals(first, second)
    }

    @Test
    fun `the hex digest is the bytes`() {
        val key = newKey()
        key.signer()
        val hex = key.certificateSha256Bytes()!!.joinToString("") { "%02x".format(it) }
        assertEquals(key.certificateSha256OrNull(), hex)
    }
}
