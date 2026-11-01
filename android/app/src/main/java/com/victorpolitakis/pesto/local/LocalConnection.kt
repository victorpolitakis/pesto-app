package com.victorpolitakis.pesto.local

import android.os.SystemClock
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.backend.BackendConnection
import com.victorpolitakis.pesto.backend.DictationMode
import com.victorpolitakis.pesto.backend.Incoming
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.data.SearchText
import com.victorpolitakis.pesto.di.AppScope
import com.victorpolitakis.pesto.protocol.Cancel
import com.victorpolitakis.pesto.protocol.ClientMessage
import com.victorpolitakis.pesto.protocol.Error
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Finalize
import com.victorpolitakis.pesto.protocol.Ready
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.protocol.Stats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Opens offline dictations: the model on the phone, no backend (PLAN_offline-mode.md). */
@Singleton
class LocalBackendClient @Inject constructor(
    private val transcriber: Transcriber,
    private val files: ModelFiles,
    @param:AppScope private val scope: CoroutineScope,
) : BackendClient {
    val isReady get() = files.isReady()

    override fun open(): BackendConnection =
        if (files.isReady()) LocalConnection(transcriber, scope, SystemClock::elapsedRealtime) else Missing()

    /** The model is not on the phone: the dictation ends at once, with [MODEL_MISSING]. */
    private class Missing : BackendConnection {
        private val channel = Channel<Incoming>(Channel.UNLIMITED)
        override val incoming: ReceiveChannel<Incoming> = channel
        override val mode = DictationMode.OFFLINE

        init {
            channel.trySend(Incoming.Message(Error(MODEL_MISSING, "the offline model is not on the phone", retryable = false)))
            channel.close()
        }

        override fun start(start: Start) = Unit

        override fun sendAudio(pcm: ByteArray) = Unit

        override fun send(message: ClientMessage) = Unit

        override fun close() = Unit
    }

    companion object {
        const val MODEL_MISSING = "model_missing"

        /** The model did not load, or `:asr` died during the dictation. */
        const val MODEL_FAILED = "model_failed"
    }
}

/**
 * One offline dictation, with the same messages as the backend's, so DictationSession
 * does not know the difference. The audio stays here until ✓, then goes to the model
 * at once. No partials yet (TODO(X14): text while speaking, as `partial`).
 */
class LocalConnection(
    private val transcriber: Transcriber,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) : BackendConnection {
    private val channel = Channel<Incoming>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<Incoming> = channel
    override val mode = DictationMode.OFFLINE

    private val started = CompletableDeferred<Start>()
    private val audio = ByteArrayOutputStream()
    private var finalized = false
    private var job: Job? = null

    init {
        // The model loads while the user speaks: nothing to wait for before «listening».
        transcriber.prepare()
        channel.trySend(Incoming.Message(Ready))
    }

    override fun start(start: Start) {
        started.complete(start)
    }

    /** Pause sends nothing: DictationSession stops the audio itself. */
    override fun sendAudio(pcm: ByteArray) {
        if (!finalized) audio.write(pcm)
    }

    override fun send(message: ClientMessage) {
        when (message) {
            Finalize -> finalize()
            Cancel -> close()
            else -> Unit
        }
    }

    override fun close() {
        job?.cancel()
        if (!channel.isClosedForSend) transcriber.release()
        channel.close()
    }

    private fun finalize() {
        if (finalized) return
        finalized = true
        val at = clock()
        val pcm = audio.toByteArray()
        job = scope.launch {
            val start = started.await()
            val result = transcriber.transcribe(pcm)
            channel.trySend(Incoming.Message(result?.let { final(start, it, pcm.size, clock() - at) } ?: failed()))
            transcriber.release()
            channel.close()
        }
    }

    private fun final(start: Start, result: Transcript, bytes: Int, totalMs: Long): Final {
        val text = Pcm.text(result.text)
        return Final(
            rawText = text,
            text = text,
            profile = start.profile,
            // No profiles offline yet: the History says why it was not applied (§4β).
            fallbackReason = if (start.profile == ProfilesCatalog.RAW) null else OFFLINE,
            endReason = "user",
            stats = Stats(
                audioS = Pcm.seconds(bytes),
                words = SearchText.words(text),
                sttMs = result.sttMs,
                llmMs = null,
                totalMs = totalMs.toInt(),
            ),
        )
    }

    private fun failed() = Error(LocalBackendClient.MODEL_FAILED, "the offline model failed", retryable = true)

    companion object {
        /** The fallback reason of a profile that was not applied offline. */
        const val OFFLINE = "offline"
    }
}
