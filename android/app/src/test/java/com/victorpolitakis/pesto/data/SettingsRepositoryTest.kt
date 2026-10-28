package com.victorpolitakis.pesto.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.victorpolitakis.pesto.overlay.OverlayMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** C11 on a real DataStore file, on the JVM. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun TestScope.settings() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
        backgroundScope,
    )

    @Test
    fun `the defaults are what phase 2 sent`() = runTest {
        val s = settings().stored.first()
        assertEquals(AppSettings(), s)
        assertEquals(listOf("el", "en"), s.languageHints)
        assertEquals(false, s.strictLanguages)
        assertEquals("raw", s.defaultProfile)
        assertEquals(OverlayMethod.ACCESSIBILITY, s.overlayMethod)
    }

    @Test
    fun `the languages give the language hints`() = runTest {
        val repo = settings()
        repo.setSecondaryLanguage("de")
        assertEquals(listOf("el", "de"), repo.stored.first().languageHints)
        repo.setSecondaryLanguage(null)
        assertEquals(listOf("el"), repo.stored.first().languageHints)
        repo.setPrimaryLanguage("en")
        repo.setSecondaryLanguage("en")
        assertEquals(listOf("en"), repo.stored.first().languageHints)
        repo.setStrictLanguages(true)
        assertTrue(repo.stored.first().strictLanguages)
    }

    @Test
    fun `the second language as the primary swaps the two`() = runTest {
        val repo = settings()
        repo.setPrimaryLanguage("en")
        assertEquals(listOf("en", "el"), repo.stored.first().languageHints)
        repo.setPrimaryLanguage("de")
        assertEquals(listOf("de", "el"), repo.stored.first().languageHints)
    }

    @Test
    fun `the default profile`() = runTest {
        val repo = settings()
        repo.setDefaultProfile("mail")
        assertEquals("mail", repo.stored.first().defaultProfile)
    }

    @Test
    fun `the overlay method is in memory for the service`() = runTest {
        val repo = settings()
        assertEquals(OverlayMethod.ACCESSIBILITY, repo.current())
        repo.setOverlayMethod(OverlayMethod.APPLICATION)
        advanceUntilIdle()
        assertEquals(OverlayMethod.APPLICATION, repo.current())
    }

    @Test
    fun `every language of the list has a name`() {
        assertEquals(listOf("el", "en", "de", "fr", "es", "it"), Languages.all.map { it.code })
        assertEquals("de", Languages.find("de")?.code)
    }
}
