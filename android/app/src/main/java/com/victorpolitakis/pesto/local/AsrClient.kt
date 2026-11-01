package com.victorpolitakis.pesto.local

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.victorpolitakis.pesto.di.AppScope
import com.victorpolitakis.pesto.util.PestoLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** What an offline dictation needs from the model. An interface, so LocalConnection is tested without Android. */
interface Transcriber {
    /** Starts loading the model, if it is not loaded: at the tap, or with the experimental preload. */
    fun prepare()

    /** The text of [pcm] (pcm_s16le), or null if the model did not load or its process died. */
    suspend fun transcribe(pcm: ByteArray): Transcript?

    /** Nothing needs it now. It stays loaded for a while, for the next dictation, then goes. */
    fun release()
}

/** [text] as the model wrote it. [sttMs]: the recognition only, in `:asr`. */
data class Transcript(val text: String, val sttMs: Int)

/**
 * The app side of AsrService: binds it in `:asr`, sends the audio, waits for the text.
 * On the main thread, like the rest of a dictation.
 */
@Singleton
class AsrClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val log: PestoLog,
    @param:AppScope private val scope: CoroutineScope,
) : Transcriber {
    private var binding: Binding? = null
    private var unbind: Job? = null

    override fun prepare() {
        unbind?.cancel()
        if (binding == null) binding = Binding().also { it.bind() }
    }

    override suspend fun transcribe(pcm: ByteArray): Transcript? {
        prepare()
        return checkNotNull(binding).transcribe(pcm)
    }

    override fun release() {
        unbind?.cancel()
        unbind = scope.launch {
            delay(KEEP_MS)
            binding?.unbind()
            binding = null
        }
    }

    private inner class Binding : ServiceConnection {
        private val service = CompletableDeferred<Messenger?>()
        private var result: CompletableDeferred<Transcript?>? = null
        private var bound = false
        private val replies = Messenger(Handler(Looper.getMainLooper(), ::answer))

        fun bind() {
            val intent = Intent(context, AsrService::class.java)
            // Never above «perceptible»: under memory pressure `:asr` goes before the
            // accessibility service (PLAN_offline-mode.md §6).
            bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE or Context.BIND_NOT_PERCEPTIBLE)
            if (!bound) dead("bind failed")
        }

        suspend fun transcribe(pcm: ByteArray): Transcript? {
            val messenger = service.await() ?: return null
            val answer = CompletableDeferred<Transcript?>()
            result = answer
            try {
                var from = 0
                while (from < pcm.size) {
                    val to = min(from + Asr.CHUNK_BYTES, pcm.size)
                    messenger.send(Message.obtain(null, Asr.AUDIO).apply { data.putByteArray(Asr.KEY_PCM, pcm.copyOfRange(from, to)) })
                    from = to
                }
                messenger.send(Message.obtain(null, Asr.DECODE).apply { replyTo = replies })
            } catch (_: RemoteException) {
                dead("send failed")
            }
            return answer.await()
        }

        fun unbind() {
            if (bound) context.unbindService(this)
            bound = false
        }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val messenger = Messenger(binder)
            try {
                messenger.send(Message.obtain(null, Asr.LOAD).apply { replyTo = replies })
                service.complete(messenger)
            } catch (_: RemoteException) {
                dead("send failed")
            }
        }

        /** `:asr` died, e.g. killed for memory: this dictation fails, the next one binds again. */
        override fun onServiceDisconnected(name: ComponentName) = dead("disconnected")

        override fun onBindingDied(name: ComponentName) = dead("binding died")

        override fun onNullBinding(name: ComponentName) = dead("null binding")

        private fun dead(why: String) {
            log.event("offline model process: $why")
            service.complete(null)
            result?.complete(null)
            unbind()
            if (binding === this) binding = null
        }

        private fun answer(msg: Message): Boolean {
            when (msg.what) {
                Asr.LOADED -> log.perf("model_load", msg.arg1.toLong())
                Asr.WARMED -> log.perf("model_warmup", msg.arg1.toLong())
                Asr.RESULT -> result?.complete(Transcript(msg.data.getString(Asr.KEY_TEXT).orEmpty(), msg.arg1))
                Asr.FAILED -> {
                    log.event("offline model failed to load")
                    result?.complete(null)
                }
            }
            return true
        }
    }

    private companion object {
        /** After a dictation, for the next one (PLAN_offline-mode.md §2). Then `:asr` ends. */
        const val KEEP_MS = 60_000L
    }
}
