package com.victorpolitakis.pesto.local

import android.app.Application
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.FakeLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The app side of `:asr`, against a fake service in the same process. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AsrClientTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val main = shadowOf(Looper.getMainLooper())

    /** The request ids `:asr` got, and what it answers to a DECODE. */
    private val decodes = mutableListOf<Int>()
    private var answer: (Message) -> Unit = {}

    private fun fakeService() {
        val service = Messenger(
            Handler(Looper.getMainLooper()) { msg ->
                if (msg.what == Asr.DECODE) {
                    decodes += msg.arg2
                    answer(msg)
                }
                true
            },
        )
        shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app, AsrService::class.java), service.binder)
    }

    private fun reply(to: Message, what: Int, id: Int, text: String = "") {
        to.replyTo.send(Message.obtain(null, what, 0, id).apply { data.putString(Asr.KEY_TEXT, text) })
    }

    /** Messages hop between the main looper (Binder, replies) and the test dispatcher (the coroutines). */
    private fun TestScope.settle() = repeat(3) {
        runCurrent()
        main.idle()
    }.also { runCurrent() }

    @Test
    fun `the result of an earlier request does not complete the next one`() = runTest {
        fakeService()
        val client = AsrClient(app, FakeLog(), backgroundScope)
        answer = { msg ->
            // A late answer of the dictation before, then the right one.
            reply(msg, Asr.RESULT, msg.arg2 - 1, "παλιό")
            reply(msg, Asr.RESULT, msg.arg2, "νέο")
        }
        val first = async { client.transcribe(ByteArray(2)) }
        settle()
        assertEquals("νέο", first.await()?.text)
        val second = async { client.transcribe(ByteArray(2)) }
        settle()
        assertEquals("νέο", second.await()?.text)
        assertEquals(listOf(1, 2), decodes)
    }

    @Test
    fun `an unbind completes the pending result with a failure`() = runTest {
        fakeService()
        val client = AsrClient(app, FakeLog(), backgroundScope)
        answer = {} // `:asr` never answers
        client.prepare()
        val pending = async { client.transcribe(ByteArray(2)) }
        runCurrent()
        main.idle()
        client.release()
        advanceTimeBy(60_001)
        runCurrent()
        assertNull(pending.await())
    }

    @Test
    fun `release does not unbind while another holder needs the client`() = runTest {
        fakeService()
        val client = AsrClient(app, FakeLog(), backgroundScope)
        answer = {}
        client.prepare() // the preload
        client.prepare() // the dictation
        val pending = async { client.transcribe(ByteArray(2)) }
        runCurrent()
        main.idle()
        client.release() // the button hides at the tap
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(false, pending.isCompleted)
        client.release() // the dictation ends
        advanceTimeBy(60_001)
        runCurrent()
        assertNull(pending.await())
    }
}
