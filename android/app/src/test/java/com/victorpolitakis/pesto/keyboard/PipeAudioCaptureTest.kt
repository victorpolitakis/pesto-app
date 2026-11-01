package com.victorpolitakis.pesto.keyboard

import com.victorpolitakis.pesto.audio.level
import com.victorpolitakis.pesto.protocol.FRAME_BYTES
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** The keyboard's audio through the pipe, in the microphone's 100 ms frames. */
class PipeAudioCaptureTest {
    private fun pcm(bytes: Int) = ByteArray(bytes) { (it * 37).toByte() }

    @Test
    fun `the stream is cut into 100 ms frames, the rest is the last one`() = runBlocking {
        var ended = 0
        val audio = pcm(FRAME_BYTES * 2 + 500)
        val frames = PipeAudioCapture(ByteArrayInputStream(audio)) { ended++ }.frames().toList()
        assertEquals(listOf(FRAME_BYTES, FRAME_BYTES, 500), frames.map { it.pcm.size })
        assertEquals(audio.toList(), frames.flatMap { it.pcm.toList() })
        assertFalse(frames.any { it.silenced })
        assertEquals(level(frames.first().pcm), frames.first().level)
        assertEquals(1, ended)
    }

    @Test
    fun `small reads of the pipe still give whole frames`() = runBlocking {
        // A pipe hands over what is there; the capture fills the frame across reads.
        val trickle = object : InputStream() {
            private val data = ByteArrayInputStream(pcm(FRAME_BYTES))

            override fun read(): Int = data.read()

            override fun read(b: ByteArray, off: Int, len: Int): Int = data.read(b, off, minOf(len, 7))
        }
        val frames = PipeAudioCapture(trickle) {}.frames().toList()
        assertEquals(listOf(FRAME_BYTES), frames.map { it.pcm.size })
    }

    @Test
    fun `an empty pipe ends at once, with no frame`() = runBlocking {
        var ended = false
        val frames = PipeAudioCapture(ByteArrayInputStream(ByteArray(0))) { ended = true }.frames().toList()
        assertTrue(frames.isEmpty())
        assertTrue(ended)
    }

    @Test
    fun `a broken pipe ends like the end of the stream`() = runBlocking {
        var ended = false
        val broken = object : InputStream() {
            private var calls = 0

            override fun read(): Int = throw IOException("broken")

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (calls++ == 0) return len.also { b.fill(1, off, off + len) }
                throw IOException("broken pipe")
            }
        }
        val frames = PipeAudioCapture(broken) { ended = true }.frames().toList()
        assertEquals(listOf(FRAME_BYTES), frames.map { it.pcm.size })
        assertTrue(ended)
    }
}
