package com.victorpolitakis.pesto.dictation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.FakeAppProfileDao
import com.victorpolitakis.pesto.FakeAudioCapture
import com.victorpolitakis.pesto.FakeBackendClient
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.FakeTermDao
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Ready
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.protocol.Stats
import dagger.Lazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DictationControllerTest {
    @get:Rule val folder = TemporaryFolder()

    private val audio = FakeAudioCapture()
    private val backend = FakeBackendClient()
    private val delivered = mutableListOf<String>()

    /** The first delivery waits for this, like an insertion that takes its time. */
    private val gate = CompletableDeferred<Unit>()

    private fun final(text: String) =
        Final(rawText = text, text = text, profile = "raw", endReason = "user", stats = Stats(1.0, 2, 150, null, 160))

    private fun TestScope.controller(): DictationController {
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
            backgroundScope,
        )
        val starts = StartBuilder(settings, DictionaryRepository(FakeTermDao()) { 0L }, ProfilesRepository(FakeAppProfileDao(), settings), FakeLog(), backgroundScope)
        val delivery = object : Delivery {
            override suspend fun deliver(outcome: Outcome, start: Start, startedAt: Long) {
                if (delivered.isEmpty()) {
                    delivered += "waiting"
                    gate.await()
                    delivered.clear()
                }
                delivered += ((outcome as Outcome.Done).final.text)
            }
        }
        return DictationController(Lazy { audio }, Lazy { backend }, Lazy { starts }, Lazy { delivery }, { 0L }, FakeLog(), backgroundScope)
    }

    @Test
    fun `the end of one dictation does not cancel the watcher of the next`() = runTest {
        val c = controller()
        assertEquals(true, c.start(Trigger.TEST, null))
        runCurrent()
        backend.server(Ready)
        c.finish()
        backend.server(final("πρώτο"))
        runCurrent()
        // The first is being delivered: no session any more, so the next one may start.
        assertEquals(false, c.isActive)
        assertEquals(true, c.start(Trigger.TEST, null))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        backend.server(Ready)
        c.finish()
        backend.server(final("δεύτερο"))
        runCurrent()
        assertEquals(listOf("πρώτο", "δεύτερο"), delivered)
    }
}
