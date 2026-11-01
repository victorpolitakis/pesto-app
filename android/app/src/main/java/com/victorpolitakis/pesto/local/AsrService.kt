package com.victorpolitakis.pesto.local

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream

/** The messages between AsrClient (app process) and AsrService (`:asr`). */
internal object Asr {
    /** → load and, if no audio is waiting, warm up. ← [LOADED], [WARMED] or [FAILED]. */
    const val LOAD = 1

    /** → a piece of the dictation's pcm_s16le, in [KEY_PCM]: the Binder takes ~1 MB at most. */
    const val AUDIO = 2

    /** → recognize the pieces sent so far, then forget them. ← [RESULT] or [FAILED]. */
    const val DECODE = 3

    /** ← arg1: ms of the load. */
    const val LOADED = 10

    /** ← arg1: ms of the warm-up. */
    const val WARMED = 11

    /** ← [KEY_TEXT], arg1: ms of the recognition. */
    const val RESULT = 12

    /** ← the model did not load. */
    const val FAILED = 13

    const val KEY_PCM = "pcm"
    const val KEY_TEXT = "text"

    /** 2 s of audio per [AUDIO] message. */
    const val CHUNK_BYTES = 64_000
}

/**
 * The offline model in a process of its own (`android:process=":asr"`): when memory is
 * short, the low memory killer takes this one and not the accessibility service (spike
 * of 31/10, PLAN_offline-mode.md §6). Bound only by AsrClient; once unbound, the
 * process ends, so the ~1 GB of the model is surely given back.
 */
class AsrService : Service() {
    private val thread = HandlerThread("asr").apply { start() }
    private val audio = ByteArrayOutputStream()
    private lateinit var model: SpeechModel
    private lateinit var handler: Handler

    override fun onCreate() {
        super.onCreate()
        model = SpeechModel(ModelStore(this).dir)
        handler = Handler(thread.looper, ::handle)
    }

    override fun onBind(intent: Intent): IBinder = Messenger(handler).binder

    override fun onDestroy() {
        super.onDestroy()
        // Nothing binds it any more: the process goes, and its memory with it.
        Process.killProcess(Process.myPid())
    }

    private fun handle(msg: Message): Boolean {
        when (msg.what) {
            Asr.LOAD -> if (load(msg)) {
                // At the ✓ before the warm-up: the dictation goes first.
                if (!handler.hasMessages(Asr.AUDIO) && !handler.hasMessages(Asr.DECODE)) {
                    val t0 = SystemClock.elapsedRealtime()
                    model.warmUp()
                    reply(msg, Asr.WARMED, (SystemClock.elapsedRealtime() - t0).toInt())
                }
            }
            Asr.AUDIO -> msg.data.getByteArray(Asr.KEY_PCM)?.let { audio.write(it) }
            Asr.DECODE -> if (load(msg)) {
                val t0 = SystemClock.elapsedRealtime()
                val text = model.decode(Pcm.samples(audio.toByteArray()))
                audio.reset()
                reply(msg, Asr.RESULT, (SystemClock.elapsedRealtime() - t0).toInt()) { putString(Asr.KEY_TEXT, text) }
            } else {
                audio.reset()
            }
        }
        return true
    }

    /** False if the model did not load: the client hears [Asr.FAILED]. */
    private fun load(msg: Message): Boolean {
        if (model.isLoaded) return true
        val t0 = SystemClock.elapsedRealtime()
        return try {
            model.load()
            reply(msg, Asr.LOADED, (SystemClock.elapsedRealtime() - t0).toInt())
            true
        } catch (e: Exception) {
            Log.e("Pesto", "offline model failed to load: ${e.javaClass.simpleName}")
            reply(msg, Asr.FAILED, 0)
            false
        }
    }

    private fun reply(to: Message, what: Int, arg1: Int, data: Bundle.() -> Unit = {}) {
        val answer = Message.obtain(null, what, arg1, 0).apply { this.data.data() }
        try {
            to.replyTo?.send(answer)
        } catch (_: RemoteException) {
            // The app process is gone: nobody waits for the answer.
        }
    }
}
