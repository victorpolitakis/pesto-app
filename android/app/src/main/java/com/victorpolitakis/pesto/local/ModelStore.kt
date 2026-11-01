package com.victorpolitakis.pesto.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Where the files of the offline model are (PLAN_offline-mode.md §3), and whether they are all there. */
interface ModelFiles {
    fun isReady(): Boolean
}

/**
 * The offline model: myned-ai/parakeet-tdt-0.6b-v3-greek, int4, for sherpa-onnx (CC-BY-4.0).
 * Phase A: `adb push` into [dir]. TODO(X13): the download inside the app, with the sha256.
 */
@Singleton
class ModelStore @Inject constructor(@ApplicationContext context: Context) : ModelFiles {
    /**
     * Made by the app itself: the app cannot read what `adb push` puts into a folder
     * adb made (owner `shell`, spike of 31/10). Settings makes it, before the push.
     */
    val dir: File = File(context.getExternalFilesDir(null), "models/$NAME").also { it.mkdirs() }

    /**
     * All the files, each with its size. Not their sha256: reading 750 MB at every
     * tap takes seconds, and X13 checks it once, on the download.
     */
    override fun isReady(): Boolean = FILES.all { (name, size) -> File(dir, name).length() == size }

    /** Bytes on the phone, for Settings. */
    fun size(): Long = FILES.keys.sumOf { File(dir, it).length() }

    companion object {
        const val NAME = "parakeet-el-int4"
        const val ENCODER = "encoder-model.int4.onnx"
        const val DECODER = "decoder.onnx"
        const val JOINER = "joiner.onnx"
        const val TOKENS = "tokens.txt"

        /** The files of side-testing/artifacts/myned_parakeet_greek_int4_sherpa, and their sizes. */
        val FILES = mapOf(
            ENCODER to 41_895_571L,
            "$ENCODER.data" to 637_636_608L,
            DECODER to 47_321_687L,
            JOINER to 25_372_877L,
            TOKENS to 94_277L,
        )
    }
}
