package com.victorpolitakis.pesto.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.FakeBackendClient
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.FakeModelFiles
import com.victorpolitakis.pesto.FakeTranscriber
import com.victorpolitakis.pesto.backend.DictationMode
import com.victorpolitakis.pesto.backend.Incoming
import com.victorpolitakis.pesto.protocol.Error
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** §4: Soniox by default, the phone without a network or with the setting. Robolectric for SystemClock. */
@RunWith(AndroidJUnit4::class)
class RoutingBackendClientTest {
    private val online = FakeBackendClient()
    private val files = FakeModelFiles()
    private val model = FakeTranscriber()
    private val log = FakeLog()
    private var offlineDefault = false
    private var internet = true

    private fun TestScope.router() = RoutingBackendClient(
        online,
        LocalBackendClient(model, files, backgroundScope),
        offlineDefault = { offlineDefault },
        network = { internet },
        log = log,
    )

    @Test
    fun `by default Soniox, as before`() = runTest {
        assertEquals(DictationMode.ONLINE, router().open().mode)
        assertEquals(emptyList<String>(), model.calls)
        assertTrue("event route online" in log.lines)
    }

    @Test
    fun `with the setting, the phone`() = runTest {
        offlineDefault = true
        assertEquals(DictationMode.OFFLINE, router().open().mode)
        assertEquals(listOf("prepare"), model.calls)
        assertTrue("event route offline reason=setting" in log.lines)
    }

    @Test
    fun `without a network, the phone, whatever the setting`() = runTest {
        internet = false
        assertEquals(DictationMode.OFFLINE, router().open().mode)
        assertTrue("event route offline reason=no_network" in log.lines)
    }

    @Test
    fun `with the setting but no model, Soniox while there is a network`() = runTest {
        offlineDefault = true
        files.ready = false
        assertEquals(DictationMode.ONLINE, router().open().mode)
    }

    @Test
    fun `without a network and without the model, model_missing at once`() = runTest {
        internet = false
        files.ready = false
        val c = router().open()
        assertEquals(DictationMode.OFFLINE, c.mode)
        val first = c.incoming.tryReceive().getOrNull() as Incoming.Message
        assertEquals(LocalBackendClient.MODEL_MISSING, (first.message as Error).code)
        assertTrue(c.incoming.isClosedForReceive)
        assertEquals(emptyList<String>(), model.calls)
    }

    @Test
    fun `the preload asks the same question`() = runTest {
        val r = router()
        assertFalse(r.wouldLoadModel())
        offlineDefault = true
        assertTrue(r.wouldLoadModel())
        offlineDefault = false
        internet = false
        assertTrue(r.wouldLoadModel())
        files.ready = false
        assertFalse(r.wouldLoadModel())
    }
}
