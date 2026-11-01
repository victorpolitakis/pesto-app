package com.victorpolitakis.pesto.local

import com.victorpolitakis.pesto.FakeAudioCapture
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.FakeTranscriber
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.backend.BackendConnection
import com.victorpolitakis.pesto.backend.DictationMode
import com.victorpolitakis.pesto.backend.Incoming
import com.victorpolitakis.pesto.dictation.DictationSession
import com.victorpolitakis.pesto.dictation.Outcome
import com.victorpolitakis.pesto.dictation.Phase
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Ready
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.protocol.Stats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An offline dictation through the real DictationSession: the same messages as the backend's (§2). */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalConnectionTest {
    private val audio = FakeAudioCapture()
    private val model = FakeTranscriber()
    private val log = FakeLog()
    private var connection: BackendConnection? = null

    private fun TestScope.session(start: Deferred<Start> = CompletableDeferred(Start())): DictationSession {
        val local = object : BackendClient {
            override fun open() = LocalConnection(model, backgroundScope, clock = { testScheduler.currentTime }).also { connection = it }
        }
        return DictationSession(start, audio, local, backgroundScope, log, clock = { testScheduler.currentTime }).also {
            it.start()
            runCurrent()
        }
    }

    @Test
    fun `ready at once, the model starts loading, the final only after the check mark`() = runTest {
        val s = session()
        assertEquals(listOf("prepare"), model.calls)
        assertEquals(Phase.LISTENING, s.state.value.phase)
        audio.speak()
        audio.speak()
        runCurrent()
        assertFalse(s.outcome.isCompleted)
        assertEquals(DictationMode.OFFLINE, connection?.mode)

        advanceTimeBy(500)
        s.finish()
        runCurrent()
        val done = s.outcome.await() as Outcome.Done
        assertEquals(DictationMode.OFFLINE, done.mode)
        assertEquals(
            Final("Καλημέρα Γιώργο", "Καλημέρα Γιώργο", "raw", null, "user", Stats(0.2, 2, 420, null, 0)),
            done.final,
        )
        assertEquals(6400, model.heard?.size)
        assertEquals(listOf("prepare", "transcribe", "release"), model.calls)
        assertTrue("perf finalize_to_final" in log.lines)
    }

    @Test
    fun `the text has the Greek question mark`() = runTest {
        model.text = "Τι ώρα είναι?"
        val s = session()
        audio.speak()
        runCurrent()
        s.finish()
        runCurrent()
        assertEquals("Τι ώρα είναι;", (s.outcome.await() as Outcome.Done).final.text)
    }

    @Test
    fun `a profile is not applied offline, and the fallback says why`() = runTest {
        val s = session(CompletableDeferred(Start(profile = "mail")))
        audio.speak()
        runCurrent()
        s.finish()
        runCurrent()
        val final = (s.outcome.await() as Outcome.Done).final
        assertEquals("mail", final.profile)
        assertEquals(LocalConnection.OFFLINE, final.fallbackReason)
        assertEquals(final.rawText, final.text)
    }

    @Test
    fun `the check mark before the start message waits for it`() = runTest {
        val start = CompletableDeferred<Start>()
        val s = session(start)
        audio.speak()
        runCurrent()
        s.finish()
        runCurrent()
        assertFalse(s.outcome.isCompleted)
        start.complete(Start(profile = "raw"))
        runCurrent()
        assertTrue(s.outcome.await() is Outcome.Done)
    }

    @Test
    fun `pause and resume keep the audio of both sides, and the dictation goes on`() = runTest {
        val s = session()
        audio.speak()
        runCurrent()
        s.pause()
        runCurrent()
        assertEquals(Phase.PAUSED, s.state.value.phase)
        s.resume()
        runCurrent()
        assertEquals(Phase.LISTENING, s.state.value.phase)
        audio.speak()
        runCurrent()
        s.finish()
        runCurrent()
        assertTrue(s.outcome.await() is Outcome.Done)
        assertEquals(2 * 3200, model.heard?.size)
    }

    @Test
    fun `cancel gives no final and frees the model`() = runTest {
        val s = session()
        audio.speak()
        runCurrent()
        s.cancel()
        runCurrent()
        assertEquals(Outcome.Cancelled, s.outcome.await())
        assertEquals(listOf("prepare", "release"), model.calls)
    }

    @Test
    fun `a model that failed or died ends as model_failed`() = runTest {
        model.text = null
        val s = session()
        audio.speak()
        runCurrent()
        s.finish()
        runCurrent()
        assertEquals(Outcome.Failed(LocalBackendClient.MODEL_FAILED), s.outcome.await())
        assertEquals(listOf("prepare", "transcribe", "release"), model.calls)
    }

    @Test
    fun `ready is the first thing that comes`() = runTest {
        val c = LocalConnection(model, backgroundScope, clock = { 0 })
        assertEquals(Incoming.Message(Ready), c.incoming.tryReceive().getOrNull())
    }
}
