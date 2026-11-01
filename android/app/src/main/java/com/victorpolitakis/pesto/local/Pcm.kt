package com.victorpolitakis.pesto.local

import com.victorpolitakis.pesto.protocol.SAMPLE_RATE

/** The audio and the text of an offline dictation, as plain functions for the JVM tests. */
object Pcm {
    /**
     * 0.3 s of silence at each end: without it, a clip cut right at the speech comes
     * out empty (side test GUI, PLAN_offline-mode.md §2).
     */
    const val PAD_SAMPLES = SAMPLE_RATE * 3 / 10

    /** pcm_s16le → floats in [-1, 1), with [pad] zeros before and after. */
    fun samples(pcm: ByteArray, pad: Int = PAD_SAMPLES): FloatArray {
        val n = pcm.size / 2
        val out = FloatArray(n + 2 * pad)
        for (i in 0 until n) {
            val s = (pcm[2 * i].toInt() and 0xff) or (pcm[2 * i + 1].toInt() shl 8)
            out[pad + i] = s.toShort() / 32768f
        }
        return out
    }

    /** Seconds of pcm_s16le, without the padding. */
    fun seconds(bytes: Int): Double = bytes / 2.0 / SAMPLE_RATE

    /** The Greek question mark is `;`. One space between words, none at the ends. */
    fun text(model: String): String = model.replace('?', ';').trim().replace(Regex("\\s+"), " ")
}
