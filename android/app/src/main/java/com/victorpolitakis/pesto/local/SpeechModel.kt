package com.victorpolitakis.pesto.local

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.victorpolitakis.pesto.protocol.SAMPLE_RATE
import java.io.File

/**
 * sherpa-onnx's recognizer for the Parakeet transducer, in the `:asr` process only
 * (AsrService). Not thread safe: one thread uses it.
 */
class SpeechModel(private val dir: File) {
    private var recognizer: OfflineRecognizer? = null

    val isLoaded get() = recognizer != null

    /** ~2.8 s on the POCO, all of it CPU (PLAN_offline-mode.md §11). */
    fun load() {
        if (recognizer != null) return
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 128),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = path(ModelStore.ENCODER),
                    decoder = path(ModelStore.DECODER),
                    joiner = path(ModelStore.JOINER),
                ),
                tokens = path(ModelStore.TOKENS),
                numThreads = THREADS,
                modelType = "nemo_transducer",
            ),
        )
        recognizer = OfflineRecognizer(config = config)
    }

    /**
     * One second of silence. ORT sets up its memory at the first decode (~1 s more),
     * so this pays for it while the user is still speaking.
     */
    fun warmUp() {
        decode(FloatArray(SAMPLE_RATE))
    }

    fun decode(samples: FloatArray): String {
        val r = checkNotNull(recognizer) { "not loaded" }
        val stream = r.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            r.decode(stream)
            return r.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
    }

    private fun path(name: String) = File(dir, name).path

    private companion object {
        /** RTF ~0.23 on the POCO with 4 (side test). */
        const val THREADS = 4
    }
}
