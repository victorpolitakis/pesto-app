package com.victorpolitakis.pesto.keyboard

import com.victorpolitakis.pesto.audio.AudioCapture
import com.victorpolitakis.pesto.audio.AudioFrame
import com.victorpolitakis.pesto.audio.level
import com.victorpolitakis.pesto.protocol.FRAME_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.io.InputStream

/**
 * The keyboard's microphone, through a pipe: pcm_s16le mono at 16 kHz, cut into the 100 ms frames
 * of [FRAME_BYTES] that the microphone gives (AudioRecordCapture), so the dictation does not know
 * the difference. The keyboard records, because it is the visible app; Pesto never opens the mic.
 *
 * The end of the stream is the user's "Done": [onEnd] runs once, after the last frame, and the
 * caller finishes the dictation there. A pipe that breaks (the keyboard went away) ends the same
 * way. A cancelled collection does not call [onEnd].
 *
 * The flow is single use: the pipe is read once.
 */
class PipeAudioCapture(
    private val input: InputStream,
    private val onEnd: () -> Unit,
) : AudioCapture {
    override fun frames(): Flow<AudioFrame> = flow {
        while (currentCoroutineContext().isActive) {
            val pcm = ByteArray(FRAME_BYTES)
            val read = readFully(pcm)
            if (read > 0) {
                val frame = if (read == pcm.size) pcm else pcm.copyOf(read)
                // The system cannot silence the keyboard's capture behind our back: never silenced.
                emit(AudioFrame(frame, level(frame), silenced = false))
            }
            if (read < pcm.size) break
        }
        if (currentCoroutineContext().isActive) onEnd()
    }.flowOn(Dispatchers.IO)

    /** Reads until [buffer] is full or the stream ends; a broken pipe counts as the end. */
    private fun readFully(buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = try {
                input.read(buffer, read, buffer.size - read)
            } catch (e: IOException) {
                -1
            }
            if (n < 0) break
            read += n
        }
        return read
    }
}
