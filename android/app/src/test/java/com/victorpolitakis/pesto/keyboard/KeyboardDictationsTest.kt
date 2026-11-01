package com.victorpolitakis.pesto.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.FakeAppProfileDao
import com.victorpolitakis.pesto.FakeAudioCapture
import com.victorpolitakis.pesto.FakeBackendClient
import com.victorpolitakis.pesto.FakeTermDao
import com.victorpolitakis.pesto.audio.AudioCapture
import com.victorpolitakis.pesto.backend.BackendClient
import com.victorpolitakis.pesto.data.DictationHistory
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.NewDictation
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.dictation.Delivery
import com.victorpolitakis.pesto.dictation.DictationController
import com.victorpolitakis.pesto.dictation.StartBuilder
import com.victorpolitakis.pesto.dictation.Trigger
import com.victorpolitakis.pesto.insert.InsertMethod
import com.victorpolitakis.pesto.protocol.Cancel
import com.victorpolitakis.pesto.protocol.FRAME_BYTES
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Finalize
import com.victorpolitakis.pesto.protocol.Partial
import com.victorpolitakis.pesto.protocol.Ready
import com.victorpolitakis.pesto.protocol.Stats
import com.victorpolitakis.pesto.util.PestoLog
import com.victorpolitakis.pesto.util.WallClock
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * A keyboard dictation end to end, without Android's Binder: the pipe feeds the real
 * DictationController and DictationSession, a fake backend answers, and the keyboard gets the
 * replies. Real threads (the pipe is read on the IO dispatcher), so the checks wait, with a limit.
 * Robolectric: the session reads SystemClock.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardDictationsTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backend = FakeBackendClient()
    private val channel = KeyboardReplyTest.RecordingChannel()
    private val history = RecordingHistory()
    private val log = SafeLog()

    private val controller: DictationController by lazy {
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") }
        val settings = SettingsRepository(store, scope)
        val builder = StartBuilder(
            settings, DictionaryRepository(FakeTermDao()) { 0L },
            ProfilesRepository(FakeAppProfileDao(), settings), log, scope,
        )
        DictationController(
            audio = Lazy<AudioCapture> { FakeAudioCapture() },
            backend = Lazy<BackendClient> { backend },
            starts = Lazy { builder },
            delivery = Lazy<Delivery> { error("the keyboard brings its own delivery") },
            clock = WallClock { 1_000L },
            log = log,
            scope = scope,
        )
    }
    private val dictations by lazy { KeyboardDictations(controller, history, log, WallClock { 2_000L }, scope) }

    @After fun stop() = scope.cancel()

    private fun request(
        packageName: String = "com.whatsapp",
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        imeOptions: Int = 0,
        before: String = "Είμαι",
    ) = KeyboardStart(packageName, inputType, imeOptions, hint = null, textBefore = before, textAfter = "")

    private fun final(text: String) =
        Final(rawText = text, text = text, profile = "raw", endReason = "user", stats = Stats(1.0, 2, 150, null, 160))

    /** Polls [condition] for up to 5 s of real time; the lists it reads are written by other threads. */
    private fun eventually(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out waiting for: $what (replies ${channel.snapshot()}, sent ${backend.sent.toList()})")
    }

    private fun audioSent() = backend.sent.toList().count { it == "audio" }

    @Test
    fun `the pipe feeds the dictation, its end finishes it, and the keyboard gets the joined text`() {
        val pipe = TestPipe()
        assertTrue(dictations.start(request(), pipe, channel))

        pipe.write(ByteArray(FRAME_BYTES * 2))
        eventually("two frames reach the backend") { audioSent() == 2 }
        assertEquals("com.whatsapp", backend.start?.fieldContext?.packageName)

        backend.server(Ready)
        backend.server(Partial("Κα", "λά"))
        eventually("the partial reaches the keyboard") { "partial Καλά" in channel.snapshot() }

        pipe.end() // the user pressed Done on the keyboard
        eventually("the end of the pipe finalizes") { Finalize in backend.sent.toList() }

        backend.server(final("Καλά"))
        // Joined as C5 joins it: a space before, lower case mid-sentence.
        eventually("the final reaches the keyboard") { channel.snapshot().last() == "final  καλά" }
        eventually("History records it") { history.records().size == 1 }
        val record = history.records().single()
        assertEquals(InsertMethod.COMMIT_TEXT.name, record.insertMethod)
        assertEquals("com.whatsapp", record.context.packageName)
        eventually("the pipe is closed") { pipe.closed }
    }

    @Test
    fun `cancel from the keyboard sends cancel, answers cancelled, and records nothing`() {
        val pipe = TestPipe()
        assertTrue(dictations.start(request(), pipe, channel))
        pipe.write(ByteArray(FRAME_BYTES))
        eventually("audio reaches the backend") { audioSent() == 1 }

        dictations.cancel()
        eventually("the keyboard is told") { channel.snapshot().lastOrNull() == "error cancelled" }
        assertTrue(Cancel in backend.sent.toList())
        eventually("the pipe is closed") { pipe.closed }
        // A late final changes nothing.
        backend.server(final("Καλά"))
        Thread.sleep(100)
        assertEquals(1, channel.snapshot().count { it.startsWith("final") || it.startsWith("error") })
        assertTrue(history.records().isEmpty())
    }

    @Test
    fun `the keyboard going away is a cancel`() {
        val pipe = TestPipe()
        assertTrue(dictations.start(request(), pipe, channel))
        dictations.clientGone()
        eventually("the dictation ends") { channel.snapshot().lastOrNull() == "error cancelled" }
        assertTrue(history.records().isEmpty())
    }

    @Test
    fun `a password field is refused before anything starts`() {
        val pipe = TestPipe()
        val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertFalse(dictations.start(request(inputType = password), pipe, channel))
        assertEquals(listOf("error password"), channel.snapshot())
        assertTrue(pipe.closed)
        Thread.sleep(100)
        assertNull("nothing reached the backend", backend.start)
    }

    @Test
    fun `while another dictation runs, the keyboard is told busy`() {
        assertTrue(controller.start(Trigger.TEST, null))
        val pipe = TestPipe()
        assertFalse(dictations.start(request(), pipe, channel))
        assertEquals(listOf("error busy"), channel.snapshot())
        assertTrue(pipe.closed)
    }

    @Test
    fun `an incognito field gets its text but stays out of History`() {
        val pipe = TestPipe()
        val incognito = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        assertTrue(dictations.start(request(imeOptions = incognito, before = ""), pipe, channel))
        eventually("the dictation starts") { backend.start != null }
        backend.server(Ready)
        pipe.end()
        eventually("the end of the pipe finalizes") { Finalize in backend.sent.toList() }
        backend.server(final("Καλά"))
        eventually("the final reaches the keyboard") { channel.snapshot().lastOrNull() == "final Καλά" }
        Thread.sleep(100)
        assertTrue(history.records().isEmpty())
    }

    @Test
    fun `a backend that never answers ends with its reason`() {
        val pipe = TestPipe()
        assertTrue(dictations.start(request(), pipe, channel))
        eventually("the dictation starts") { backend.start != null }
        backend.drop()
        eventually("the keyboard is told") { channel.snapshot().lastOrNull() == "error no_server" }
        eventually("the pipe is closed") { pipe.closed }
    }

    @Test
    fun `after one ends, the keyboard can dictate again`() {
        val first = TestPipe()
        assertTrue(dictations.start(request(), first, channel))
        dictations.cancel()
        eventually("the first ends") { first.closed && !controller.isActive }
        assertTrue(dictations.start(request(), TestPipe(), channel))
    }

    /** A pipe the test writes into; closing it, or [end], ends the stream, as the keyboard's does. */
    private class TestPipe : PcmSource {
        private val chunks = LinkedBlockingQueue<ByteArray>()
        private var current: ByteArray = ByteArray(0)
        private var position = 0

        @Volatile var closed = false

        fun write(bytes: ByteArray) = chunks.put(bytes)

        fun end() = chunks.put(EOF)

        override val input: InputStream = object : InputStream() {
            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                while (position >= current.size) {
                    if (closed) return -1
                    val next = chunks.poll(20, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                    if (next === EOF) return -1
                    current = next
                    position = 0
                }
                val n = minOf(len, current.size - position)
                System.arraycopy(current, position, b, off, n)
                position += n
                return n
            }
        }

        override fun close() {
            closed = true
        }

        private companion object {
            val EOF = ByteArray(0)
        }
    }

    private class RecordingHistory : DictationHistory {
        private val recorded = mutableListOf<NewDictation>()

        override suspend fun record(dictation: NewDictation) {
            synchronized(this) { recorded += dictation }
        }

        @Synchronized
        fun records() = recorded.toList()
    }

    /** FakeLog, safe across the dispatcher's threads. */
    private class SafeLog : PestoLog {
        override fun button(message: String) = Unit

        override fun perf(name: String, ms: Long) = Unit

        override fun event(message: String) = Unit
    }
}
