package com.victorpolitakis.pesto.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.FakeBackendClient
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.FakeModelFiles
import com.victorpolitakis.pesto.FakeTranscriber
import com.victorpolitakis.pesto.data.SettingsRepository
import dagger.Lazy
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

/** §9: the experimental preload, off by default, only for a dictation that would run offline. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OfflinePreloadTest {
    @get:Rule val folder = TemporaryFolder()

    private val model = FakeTranscriber()
    private var internet = true

    private suspend fun TestScope.preload(on: Boolean): OfflinePreload {
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
            backgroundScope,
        )
        settings.setPreloadOffline(on)
        runCurrent()
        val local = LocalBackendClient(model, FakeModelFiles(), backgroundScope)
        val router = RoutingBackendClient(FakeBackendClient(), local, { settings.settings.value.offlineDefault }, { internet }, FakeLog())
        return OfflinePreload(settings, Lazy { router }, Lazy { model })
    }

    @Test
    fun `off by default, nothing loads before the tap`() = runTest {
        internet = false
        val p = preload(on = false)
        p.buttonShown()
        p.buttonHidden()
        assertEquals(emptyList<String>(), model.calls)
    }

    @Test
    fun `on, with a network and Soniox, nothing loads`() = runTest {
        val p = preload(on = true)
        p.buttonShown()
        assertEquals(emptyList<String>(), model.calls)
    }

    @Test
    fun `on and offline, one load when the button shows, freed when it goes`() = runTest {
        internet = false
        val p = preload(on = true)
        p.buttonShown()
        p.buttonShown()
        assertEquals(listOf("prepare"), model.calls)
        p.buttonHidden()
        assertEquals(listOf("prepare", "release"), model.calls)
    }
}
