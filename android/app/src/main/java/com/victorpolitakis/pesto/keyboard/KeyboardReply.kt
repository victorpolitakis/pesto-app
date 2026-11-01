package com.victorpolitakis.pesto.keyboard

import com.victorpolitakis.pesto.dictation.Delivery
import com.victorpolitakis.pesto.dictation.Outcome
import com.victorpolitakis.pesto.insert.InsertMethod
import com.victorpolitakis.pesto.insert.Inserter
import com.victorpolitakis.pesto.insert.TextJoiner
import com.victorpolitakis.pesto.protocol.Start
import java.util.concurrent.atomic.AtomicBoolean

/** Where the replies to the keyboard go; the service sends them through the keyboard's Messenger. */
interface KeyboardChannel {
    fun send(what: Int, text: String? = null, level: Float? = null, reason: String? = null)
}

/**
 * The replies of one keyboard dictation. Partials and levels while it runs, then exactly one end,
 * [MSG_FINAL][KeyboardProtocol.MSG_FINAL] or [MSG_ERROR][KeyboardProtocol.MSG_ERROR], so the panel
 * of the keyboard always closes. Nothing is sent after the end.
 */
class KeyboardReply(private val channel: KeyboardChannel) {
    private val ended = AtomicBoolean(false)
    private var lastPartial: String? = null

    val isEnded get() = ended.get()

    fun partial(text: String) {
        if (isEnded || text == lastPartial) return
        lastPartial = text
        channel.send(KeyboardProtocol.MSG_PARTIAL, text = text)
    }

    fun level(level: Float) {
        if (!isEnded) channel.send(KeyboardProtocol.MSG_LEVEL, level = level)
    }

    fun final(piece: String) {
        if (ended.compareAndSet(false, true)) channel.send(KeyboardProtocol.MSG_FINAL, text = piece)
    }

    fun error(reason: String) {
        if (ended.compareAndSet(false, true)) channel.send(KeyboardProtocol.MSG_ERROR, reason = reason)
    }

    /** The end for an outcome that wrote nothing: an error for a failure or a cancel, else no text. */
    fun endWithout(outcome: Outcome) = when (outcome) {
        is Outcome.Failed -> error(outcome.reason)
        Outcome.Cancelled -> error(KeyboardProtocol.REASON_CANCELLED)
        is Outcome.Done, is Outcome.ConnectionLost -> final("")
    }
}

/**
 * Inserts through the keyboard: Pesto joins the text to what is around the cursor (C5, TextJoiner,
 * with the text the keyboard sent at the start) and the keyboard commits the piece as is. The user
 * cannot type meanwhile: the keyboard's panel covers the keys.
 */
class KeyboardInserter(
    private val before: String,
    private val after: String,
    private val reply: KeyboardReply,
) : Inserter {
    override suspend fun insert(text: String, terms: Set<String>): InsertMethod {
        reply.final(TextJoiner.join(before, after, text, terms))
        return InsertMethod.COMMIT_TEXT
    }
}

/**
 * The usual delivery (insertion, then History, except incognito, C7), with the keyboard as the
 * inserter. Whatever happens, the keyboard gets its end: an outcome without text (✕, a failure, a
 * blank final) never reaches the inserter, so it is answered here.
 */
class KeyboardDelivery(
    private val inner: Delivery,
    private val reply: KeyboardReply,
    private val onEnd: () -> Unit,
) : Delivery {
    override suspend fun deliver(outcome: Outcome, start: Start, startedAt: Long) {
        try {
            inner.deliver(outcome, start, startedAt)
        } finally {
            reply.endWithout(outcome)
            onEnd()
        }
    }
}
