package com.victorpolitakis.pesto.keyboard

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.victorpolitakis.pesto.util.PestoLog
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import java.io.InputStream
import javax.inject.Inject

/**
 * Dictation for the patched keyboard, "Gboard with Pesto" (PLAN_patch-keyboard-impl.md, phase 2).
 * The keyboard binds it by explicit intent and talks to it with Messenger ([KeyboardProtocol]):
 * the keyboard records, because it is the visible window, and streams the audio through a pipe;
 * Pesto runs the dictation and sends back the piece of text to commit.
 *
 * Exported, because another app binds it. Every message is checked: only an app signed with the
 * keyboard's local key gets an answer ([KeyboardCallerCheck]). Light while idle: the dictation
 * pieces are created on the first start.
 */
@AndroidEntryPoint
class PestoDictationService : Service() {
    @Inject lateinit var callers: KeyboardCallerCheck
    @Inject lateinit var dictations: Lazy<KeyboardDictations>
    @Inject lateinit var log: PestoLog

    private val messenger by lazy { Messenger(Handler(Looper.getMainLooper(), ::handle)) }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    /** The keyboard unbound, or its process died: no dictation goes on without it. */
    override fun onUnbind(intent: Intent?): Boolean {
        dictations.get().clientGone()
        return false
    }

    private fun handle(message: Message): Boolean {
        // The sender, set by Binder; Binder.getCallingUid() here would be Pesto's own uid.
        val uid = message.sendingUid
        if (!callers.isAllowed(uid)) {
            // No answer, and nothing of the message is read.
            log.event("keyboard message refused: uid $uid is not signed with the keyboard's key")
            return true
        }
        when (message.what) {
            KeyboardProtocol.MSG_START -> start(message)
            KeyboardProtocol.MSG_CANCEL -> dictations.get().cancel()
            else -> log.event("keyboard message ignored: what=${message.what}")
        }
        return true
    }

    private fun start(message: Message) {
        val replyTo = message.replyTo ?: return log.event("keyboard start without replyTo")
        val channel = MessengerChannel(replyTo, log)
        val data: Bundle = message.data
        val pcm = try {
            data.getParcelable(KeyboardProtocol.KEY_PCM_FD, ParcelFileDescriptor::class.java)
        } catch (e: RuntimeException) {
            null
        }
        if (pcm == null) {
            channel.send(KeyboardProtocol.MSG_ERROR, reason = KeyboardProtocol.REASON_BAD_REQUEST)
            return
        }
        val request = KeyboardStart(
            packageName = data.getString(KeyboardProtocol.KEY_PACKAGE),
            inputType = data.getInt(KeyboardProtocol.KEY_INPUT_TYPE),
            imeOptions = data.getInt(KeyboardProtocol.KEY_IME_OPTIONS),
            hint = data.getString(KeyboardProtocol.KEY_HINT),
            // The keyboard sends at most CONTEXT_CHARS; never keep more than that.
            textBefore = data.getString(KeyboardProtocol.KEY_TEXT_BEFORE).orEmpty().takeLast(KeyboardProtocol.CONTEXT_CHARS),
            textAfter = data.getString(KeyboardProtocol.KEY_TEXT_AFTER).orEmpty().take(KeyboardProtocol.CONTEXT_CHARS),
        )
        dictations.get().start(request, PipeSource(pcm), channel)
    }
}

/** Replies through the keyboard's Messenger. A keyboard that went away is not an error. */
private class MessengerChannel(private val replyTo: Messenger, private val log: PestoLog) : KeyboardChannel {
    override fun send(what: Int, text: String?, level: Float?, reason: String?) {
        val message = Message.obtain(null, what)
        message.data = Bundle().apply {
            text?.let { putString(KeyboardProtocol.KEY_TEXT, it) }
            level?.let { putFloat(KeyboardProtocol.KEY_LEVEL, it) }
            reason?.let { putString(KeyboardProtocol.KEY_REASON, it) }
        }
        try {
            replyTo.send(message)
        } catch (e: RemoteException) {
            log.event("keyboard gone, reply what=$what dropped")
        }
    }
}

/** The read end of the keyboard's pipe. Closing it unblocks a read in progress. */
private class PipeSource(fd: ParcelFileDescriptor) : PcmSource {
    override val input: InputStream = ParcelFileDescriptor.AutoCloseInputStream(fd)

    override fun close() = input.close()
}
