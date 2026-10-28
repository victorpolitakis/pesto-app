package com.victorpolitakis.pesto.debug

import android.content.Context
import com.victorpolitakis.pesto.audio.AudioCapture
import com.victorpolitakis.pesto.audio.AudioFrame
import com.victorpolitakis.pesto.audio.AudioRecordCapture
import com.victorpolitakis.pesto.audio.level
import com.victorpolitakis.pesto.protocol.FRAME_BYTES
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject

/**
 * Debug build only: if `files/debug_audio.wav` (16 kHz mono s16le) exists, it is
 * played instead of the microphone, in real time, then silence. For the emulator
 * and the smoke test, where there is no voice. Pushed with tools/smoke.sh.
 */
class DebugAudioCapture @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mic: AudioRecordCapture,
) : AudioCapture {
    override fun frames(): Flow<AudioFrame> {
        val file = File(context.filesDir, "debug_audio.wav")
        if (!file.exists()) return mic.frames()
        return flow {
            val pcm = wavData(file.readBytes())
            var offset = 0
            while (true) {
                val chunk = ByteArray(FRAME_BYTES)
                if (offset < pcm.size) {
                    pcm.copyInto(chunk, 0, offset, minOf(offset + FRAME_BYTES, pcm.size))
                    offset += FRAME_BYTES
                }
                emit(AudioFrame(chunk, level(chunk), silenced = false))
                delay(100)
            }
        }
    }

    /** The bytes of the "data" chunk of a WAV file. */
    private fun wavData(wav: ByteArray): ByteArray {
        var i = 12
        while (i + 8 <= wav.size) {
            val id = String(wav, i, 4, Charsets.US_ASCII)
            val size = (wav[i + 4].toInt() and 0xff) or ((wav[i + 5].toInt() and 0xff) shl 8) or
                ((wav[i + 6].toInt() and 0xff) shl 16) or ((wav[i + 7].toInt() and 0xff) shl 24)
            if (id == "data") return wav.copyOfRange(i + 8, minOf(i + 8 + size, wav.size))
            i += 8 + size + (size and 1)
        }
        error("no data chunk")
    }
}

/** Debug build: the audio source above. The overlay method comes from Settings, as in release (Δ12). */
@Module
@InstallIn(SingletonComponent::class)
abstract class BuildTypeModule {
    @Binds abstract fun audio(impl: DebugAudioCapture): AudioCapture
}
