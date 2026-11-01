package com.victorpolitakis.pesto.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmTest {
    @Test
    fun `pcm_s16le becomes floats, with 0,3 s of silence at each end`() {
        // 0, 16384, -32768, 32767, little endian
        val pcm = byteArrayOf(0, 0, 0, 0x40, 0, 0x80.toByte(), 0xff.toByte(), 0x7f)
        val samples = Pcm.samples(pcm)
        assertEquals(4800, Pcm.PAD_SAMPLES)
        assertEquals(4 + 2 * 4800, samples.size)
        assertArrayEquals(FloatArray(4800), samples.copyOfRange(0, 4800), 0f)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -1f, 32767 / 32768f), samples.copyOfRange(4800, 4804), 0f)
        assertArrayEquals(FloatArray(4800), samples.copyOfRange(4804, samples.size), 0f)
    }

    @Test
    fun `the seconds are of the speech, without the padding`() {
        assertEquals(1.5, Pcm.seconds(48_000), 1e-9)
    }

    @Test
    fun `the question mark is the Greek one, and the spaces are tidied`() {
        assertEquals("Τι ώρα είναι; Στις δέκα.", Pcm.text("  Τι ώρα είναι?   Στις δέκα. "))
    }
}
