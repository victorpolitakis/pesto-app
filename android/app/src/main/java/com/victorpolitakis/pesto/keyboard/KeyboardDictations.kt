package com.victorpolitakis.pesto.keyboard

import android.text.InputType
import com.victorpolitakis.pesto.data.DictationHistory
import com.victorpolitakis.pesto.di.AppScope
import com.victorpolitakis.pesto.dictation.DictationController
import com.victorpolitakis.pesto.dictation.DictationUi
import com.victorpolitakis.pesto.dictation.FieldRules
import com.victorpolitakis.pesto.dictation.FieldState
import com.victorpolitakis.pesto.dictation.OutcomeDelivery
import com.victorpolitakis.pesto.dictation.Trigger
import com.victorpolitakis.pesto.util.PestoLog
import com.victorpolitakis.pesto.util.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/** What the keyboard sends with [KeyboardProtocol.MSG_START], without Android types. */
data class KeyboardStart(
    val packageName: String?,
    val inputType: Int,
    val imeOptions: Int,
    val hint: String?,
    val textBefore: String,
    val textAfter: String,
) {
    /** The field, as the accessibility service would see it, so the same rules apply (C6, C7, Δ5). */
    fun field() = FieldState(
        packageName = packageName.orEmpty(),
        inputType = inputType,
        imeOptions = imeOptions,
        hint = hint,
        isPassword = false, // FieldRules reads it from the input type
        isMultiLine = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0,
    )
}

/**
 * The dictations the patched keyboard asks for. The audio comes through the keyboard's pipe; the
 * rest is the dictation of the app: the same [DictationController] (one dictation at a time, with
 * the overlay's), the same start message (languages from Settings, profile per app), online or
 * offline, and the same delivery, so the History records them as always.
 */
@Singleton
class KeyboardDictations @Inject constructor(
    private val controller: DictationController,
    private val history: DictationHistory,
    private val log: PestoLog,
    private val clock: WallClock,
    @param:AppScope private val scope: CoroutineScope,
) {
    /** Written under the lock; read without it by the pipe's end callback, on the IO thread. */
    @Volatile private var active: Active? = null

    private class Active(val reply: KeyboardReply, val pcm: Closeable, val updates: Job)

    /**
     * Starts a dictation for the keyboard. [pcm] is the read end of the pipe; it is closed when the
     * dictation ends. Returns false, after answering with an error, if it could not start.
     */
    @Synchronized
    fun start(request: KeyboardStart, pcm: PcmSource, channel: KeyboardChannel): Boolean {
        val reply = KeyboardReply(channel)
        val field = request.field()
        if (FieldRules.isPassword(field)) {
            // C7: never dictate into a password field, whatever the keyboard shows.
            log.event("keyboard start refused: password field")
            reply.error(KeyboardProtocol.REASON_PASSWORD)
            pcm.close()
            return false
        }
        val inserter = KeyboardInserter(request.textBefore, request.textAfter, reply)
        val delivery = KeyboardDelivery(OutcomeDelivery(inserter, history, log, clock), reply) { ended(reply) }
        val audio = PipeAudioCapture(pcm.input) { if (active?.reply === reply) controller.finish() }
        if (!controller.start(Trigger.KEYBOARD, field, audio, delivery)) {
            log.event("keyboard start refused: busy")
            reply.error(KeyboardProtocol.REASON_BUSY)
            pcm.close()
            return false
        }
        val updates = scope.launch {
            controller.ui.collect { ui ->
                if (ui is DictationUi.Active) {
                    reply.partial(ui.live.text)
                    reply.level(ui.live.level)
                }
            }
        }
        active = Active(reply, pcm, updates)
        return true
    }

    /** ✕ from the keyboard: nothing is written and nothing is recorded. */
    @Synchronized
    fun cancel() {
        // Only while ours has not answered yet: the keyboard unbinds right after the final, and a
        // late cancel must not reach the next dictation (the overlay's, say).
        val a = active ?: return
        if (!a.reply.isEnded) controller.cancel()
    }

    /** The keyboard went away (unbound, or its process died): the same as a cancel. */
    fun clientGone() = cancel()

    @Synchronized
    private fun ended(reply: KeyboardReply) {
        val a = active ?: return
        if (a.reply !== reply) return
        active = null
        a.updates.cancel()
        // Unblocks a read of the pipe the dictation no longer wants.
        runCatching { a.pcm.close() }
    }
}

/** The read end of the keyboard's pipe: its stream, and how to close it. */
interface PcmSource : Closeable {
    val input: java.io.InputStream
}
