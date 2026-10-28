package com.victorpolitakis.pesto.dictation

import com.victorpolitakis.pesto.FakeAudioCapture
import com.victorpolitakis.pesto.FakeBackendClient
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.backend.Incoming
import com.victorpolitakis.pesto.protocol.Cancel
import com.victorpolitakis.pesto.protocol.Error
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Finalize
import com.victorpolitakis.pesto.protocol.Partial
import com.victorpolitakis.pesto.protocol.Pause
import com.victorpolitakis.pesto.protocol.Ready
import com.victorpolitakis.pesto.protocol.Resume
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.protocol.Stats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionTest {
    private val audio = FakeAudioCapture()
    private val backend = FakeBackendClient()
    private val log = FakeLog()

    private fun final(text: String, endReason: String = "user") =
        Final(rawText = text, text = text, profile = "raw", endReason = endReason, stats = Stats(1.0, 2, 150, null, 160))

    private fun TestScope.session(): DictationSession =
        DictationSession(Start(), audio, backend, backgroundScope, log, clock = { testScheduler.currentTime }).also {
            it.start()
            runCurrent()
        }

    private fun audioSent() = backend.sent.count { it == "audio" }

    @Test
    fun `start, partials and finalize give one final`() = runTest {
        val s = session()
        assertEquals("raw", backend.start?.profile)
        audio.speak()
        backend.server(Ready)
        backend.server(Partial("Καλημέρα ", "Γιώργο"))
        runCurrent()
        assertEquals(LiveState(Phase.LISTENING, "Καλημέρα Γιώργο", 0.5f), s.state.value)

        s.finish()
        backend.server(final("Καλημέρα Γιώργο."))
        runCurrent()
        assertEquals(Outcome.Done(final("Καλημέρα Γιώργο.")), s.outcome.await())
        assertEquals(listOf("audio", Finalize), backend.sent)
        assertFalse(audio.open)
        assertTrue("perf finalize_to_final" in log.lines)
    }

    @Test
    fun `audio before ready is sent at once`() = runTest {
        session()
        audio.speak()
        audio.speak()
        runCurrent()
        assertEquals(2, audioSent())
    }

    @Test
    fun `cancel sends cancel and no final`() = runTest {
        val s = session()
        backend.server(Ready)
        runCurrent()
        s.cancel()
        runCurrent()
        assertEquals(Outcome.Cancelled, s.outcome.await())
        assertEquals(listOf<Any>(Cancel), backend.sent)
        assertTrue(backend.closed)
        assertFalse(audio.open)
    }

    @Test
    fun `no audio is sent while paused`() = runTest {
        val s = session()
        backend.server(Ready)
        runCurrent()
        s.pause()
        runCurrent()
        assertFalse(audio.open)
        audio.speak() // queued, nobody records it
        runCurrent()
        assertEquals(0, audioSent())
        assertEquals(Phase.PAUSED, s.state.value.phase)

        s.resume()
        runCurrent()
        assertEquals(Phase.LISTENING, s.state.value.phase)
        assertEquals(listOf(Pause, Resume, "audio"), backend.sent)
        assertEquals(2, audio.opened)
    }

    @Test
    fun `stt error is followed by the final`() = runTest {
        val s = session()
        backend.server(Ready)
        backend.server(Partial("Μισό ", ""))
        backend.server(Error("stt", "soniox failed", retryable = true))
        backend.server(final("Μισό", endReason = "error"))
        runCurrent()
        assertEquals(Outcome.Done(final("Μισό", endReason = "error")), s.outcome.await())
    }

    @Test
    fun `the backend can end the dictation on its own`() = runTest {
        val s = session()
        backend.server(Ready)
        backend.server(final("Τέλος.", endReason = "silence"))
        runCurrent()
        assertEquals(Outcome.Done(final("Τέλος.", endReason = "silence")), s.outcome.await())
        assertFalse(audio.open)
    }

    @Test
    fun `a broken connection keeps the last text`() = runTest {
        val s = session()
        backend.server(Ready)
        backend.server(Partial("Ό,τι είπα ", "μέχρι εδώ"))
        backend.drop()
        runCurrent()
        assertEquals(Outcome.ConnectionLost("Ό,τι είπα μέχρι εδώ"), s.outcome.await())
    }

    @Test
    fun `no server before ready`() = runTest {
        val s = session()
        backend.drop()
        runCurrent()
        assertEquals(Outcome.Failed("no_server"), s.outcome.await())
    }

    @Test
    fun `a refused token fails as unauthorized, so the panel says to pair`() = runTest {
        val s = session()
        backend.drop(reason = Incoming.UNAUTHORIZED)
        runCurrent()
        assertEquals(Outcome.Failed(Incoming.UNAUTHORIZED), s.outcome.await())
    }

    @Test
    fun `a silenced microphone stops the dictation`() = runTest {
        val s = session()
        audio.speak(silenced = true)
        runCurrent()
        assertEquals(Outcome.Failed("mic_blocked"), s.outcome.await())
        assertTrue(backend.closed)
    }

    @Test
    fun `backend errors that close the connection fail the dictation`() = runTest {
        val s = session()
        backend.server(Error("stt_unavailable", "no soniox", retryable = true))
        runCurrent()
        assertEquals(Outcome.Failed("stt_unavailable"), s.outcome.await())
    }
}
