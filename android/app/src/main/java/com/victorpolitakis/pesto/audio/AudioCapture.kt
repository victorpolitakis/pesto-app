package com.victorpolitakis.pesto.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.victorpolitakis.pesto.protocol.FRAME_BYTES
import com.victorpolitakis.pesto.protocol.SAMPLE_RATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.math.sqrt

/** 100 ms of pcm_s16le mono audio, its level (0..1) for the wave, and whether the system silenced us. */
class AudioFrame(val pcm: ByteArray, val level: Float, val silenced: Boolean)

/** Microphone audio as a cold flow: collecting opens the mic, cancelling closes it. */
interface AudioCapture {
    fun frames(): Flow<AudioFrame>
}

class AudioRecordCapture @Inject constructor() : AudioCapture {
    @SuppressLint("MissingPermission") // C3 asks for RECORD_AUDIO before the button can appear
    override fun frames(): Flow<AudioFrame> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, FRAME_BYTES * 4),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialized" }
        try {
            record.startRecording()
            var frames = 0
            var silenced = false
            while (currentCoroutineContext().isActive) {
                val pcm = ByteArray(FRAME_BYTES)
                var read = 0
                while (read < pcm.size) {
                    val n = record.read(pcm, read, pcm.size - read)
                    if (n <= 0) error("AudioRecord.read returned $n")
                    read += n
                }
                // The system silences capture without an error (docs: "no audio and no error").
                if (frames++ % 10 == 0) {
                    silenced = record.activeRecordingConfiguration?.isClientSilenced == true
                }
                emit(AudioFrame(pcm, level(pcm), silenced))
            }
        } finally {
            record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}

/** RMS of a pcm_s16le buffer, scaled to 0..1 for drawing. */
fun level(pcm: ByteArray): Float {
    var sum = 0.0
    var i = 0
    while (i + 1 < pcm.size) {
        val sample = (pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)
        sum += sample.toDouble() * sample
        i += 2
    }
    val rms = sqrt(sum / (pcm.size / 2).coerceAtLeast(1))
    return (rms / 6000.0).coerceIn(0.0, 1.0).toFloat()
}
