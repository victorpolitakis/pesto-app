package com.victorpolitakis.pesto.keyboard

import com.victorpolitakis.pesto.dictation.Delivery
import com.victorpolitakis.pesto.dictation.Outcome
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.protocol.Stats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the keyboard is told: partials, then exactly one end, with the text joined as in C5. */
class KeyboardReplyTest {
    /** Every reply, as "what text/level/reason". */
    class RecordingChannel : KeyboardChannel {
        val sent = mutableListOf<String>()

        @Synchronized
        override fun send(what: Int, text: String?, level: Float?, reason: String?) {
            sent += listOfNotNull(name(what), text, level?.toString(), reason).joinToString(" ")
        }

        @Synchronized
        fun snapshot() = sent.toList()

        private fun name(what: Int) = when (what) {
            KeyboardProtocol.MSG_PARTIAL -> "partial"
            KeyboardProtocol.MSG_LEVEL -> "level"
            KeyboardProtocol.MSG_FINAL -> "final"
            KeyboardProtocol.MSG_ERROR -> "error"
            else -> "what=$what"
        }
    }

    private val channel = RecordingChannel()
    private val reply = KeyboardReply(channel)

    private fun final(text: String) =
        Final(rawText = text, text = text, profile = "raw", endReason = "user", stats = Stats(1.0, 2, 150, null, 160))

    @Test
    fun `one end only, and nothing after it`() {
        reply.partial("Καλη")
        reply.final("Καλημέρα")
        reply.final("again")
        reply.error("no_server")
        reply.partial("late")
        reply.level(0.5f)
        assertEquals(listOf("partial Καλη", "final Καλημέρα"), channel.sent)
        assertTrue(reply.isEnded)
    }

    @Test
    fun `the same partial is not sent twice`() {
        reply.partial("Καλη")
        reply.partial("Καλη")
        reply.partial("Καλημέρα")
        assertEquals(listOf("partial Καλη", "partial Καλημέρα"), channel.sent)
    }

    @Test
    fun `the inserter joins the text to the text around the cursor`() = runBlocking {
        // Mid-sentence: a space before, lower case, as the accessibility insertion does (C5, Δ6).
        KeyboardInserter(before = "Είμαι", after = "", reply = reply).insert("Καλά", emptySet())
        assertEquals(listOf("final  καλά"), channel.sent)
    }

    @Test
    fun `at the start of a sentence the first letter is a capital`() = runBlocking {
        KeyboardInserter(before = "Τέλος. ", after = "", reply = reply).insert("καλημέρα", emptySet())
        assertEquals(listOf("final Καλημέρα"), channel.sent)
    }

    private fun deliverWith(outcome: Outcome, inner: Delivery): Boolean {
        var ended = false
        runBlocking { KeyboardDelivery(inner, reply) { ended = true }.deliver(outcome, Start(), 0L) }
        return ended
    }

    /** Stands in for OutcomeDelivery: inserts a non-blank final, like it does. */
    private val inserts = object : Delivery {
        override suspend fun deliver(outcome: Outcome, start: Start, startedAt: Long) {
            if (outcome is Outcome.Done && outcome.final.text.isNotBlank()) reply.final(outcome.final.text)
        }
    }

    @Test
    fun `a dictation with text ends with what the inserter sent`() {
        assertTrue(deliverWith(Outcome.Done(final("Καλημέρα")), inserts))
        assertEquals(listOf("final Καλημέρα"), channel.sent)
    }

    @Test
    fun `a blank final still ends the keyboard's dictation, with no text`() {
        assertTrue(deliverWith(Outcome.Done(final(" ")), inserts))
        // "final" with an empty text: nothing to commit.
        assertEquals(listOf("final "), channel.sent)
    }

    @Test
    fun `a failure ends with its reason`() {
        assertTrue(deliverWith(Outcome.Failed("no_server"), inserts))
        assertEquals(listOf("error no_server"), channel.sent)
    }

    @Test
    fun `a cancel ends with cancelled`() {
        assertTrue(deliverWith(Outcome.Cancelled, inserts))
        assertEquals(listOf("error cancelled"), channel.sent)
    }

    @Test
    fun `the keyboard gets its end even if the History write fails`() {
        val failing = object : Delivery {
            override suspend fun deliver(outcome: Outcome, start: Start, startedAt: Long) {
                inserts.deliver(outcome, start, startedAt)
                error("disk full")
            }
        }
        val result = runCatching { deliverWith(Outcome.Done(final("Καλημέρα")), failing) }
        assertTrue(result.isFailure)
        assertEquals(listOf("final Καλημέρα"), channel.sent)
    }
}
