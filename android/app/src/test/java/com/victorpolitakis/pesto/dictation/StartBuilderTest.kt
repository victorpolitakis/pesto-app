package com.victorpolitakis.pesto.dictation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.victorpolitakis.pesto.FakeAppProfileDao
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.FakeTermDao
import com.victorpolitakis.pesto.data.AppProfileEntity
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.data.TermEntity
import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.protocol.MAX_TERMS_CHARS
import com.victorpolitakis.pesto.protocol.encode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The start message for each case (C2, C9, C11), as JSON, as the backend gets it. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartBuilderTest {
    @get:Rule val folder = TemporaryFolder()

    private val terms = FakeTermDao()
    private val apps = FakeAppProfileDao()
    private val whatsapp = FieldContext(packageName = "com.whatsapp", inputType = "text")

    private class Parts(val settings: SettingsRepository, val dictionary: DictionaryRepository, val builder: StartBuilder)

    private fun TestScope.parts(): Parts {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") }
        val settings = SettingsRepository(store, backgroundScope)
        val dictionary = DictionaryRepository(terms) { 0L }
        val builder = StartBuilder(settings, dictionary, ProfilesRepository(apps, settings), FakeLog(), backgroundScope)
        return Parts(settings, dictionary, builder)
    }

    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `without settings, what phase 2 sent`() = runTest {
        val start = parts().builder.build(whatsapp)
        assertEquals(
            json(
                """{"type":"start","language_hints":["el","en"],"language_hints_strict":false,"terms":[],"profile":"raw",""" +
                    """"field_context":{"package":"com.whatsapp","input_type":"text"},"sample_rate":16000,"encoding":"pcm_s16le"}""",
            ),
            json(encode(start)),
        )
    }

    @Test
    fun `the profile of the app, else the default, else raw`() = runTest {
        val p = parts()
        apps.set(AppProfileEntity("com.whatsapp", "chatter"))
        p.settings.setDefaultProfile("mail")
        assertEquals("chatter", p.builder.build(whatsapp).profile)
        assertEquals("mail", p.builder.build(FieldContext(packageName = "com.google.android.gm")).profile)
        assertEquals("mail", p.builder.build(FieldContext()).profile)
    }

    @Test
    fun `all the terms go, in the order of the Dictionary`() = runTest {
        val p = parts()
        p.dictionary.add("Πολιτάκης")
        p.dictionary.add("Pesto")
        assertEquals(listOf("Pesto", "Πολιτάκης"), p.builder.build(whatsapp).terms)
    }

    @Test
    fun `terms over the limit are left out, so the dictation never fails on them`() = runTest {
        val p = parts()
        // Straight into the table: the Dictionary screen would not let this happen.
        val big = "α".repeat(MAX_TERMS_CHARS - 2)
        terms.insert(TermEntity(term = big, termKey = "1", createdAt = 0))
        terms.insert(TermEntity(term = "Pesto", termKey = "2", createdAt = 0))
        assertEquals(listOf(big), p.builder.build(whatsapp).terms)
    }

    @Test
    fun `the second language and strict change the language hints`() = runTest {
        val p = parts()
        p.settings.setSecondaryLanguage("de")
        p.settings.setStrictLanguages(true)
        val start = p.builder.build(whatsapp)
        assertEquals(listOf("el", "de"), start.languageHints)
        assertEquals(true, start.languageHintsStrict)
    }

    @Test
    fun `after the first tap the builder follows the changes`() = runTest {
        val p = parts()
        assertEquals("raw", p.builder.build(whatsapp).profile)
        p.settings.setDefaultProfile("note")
        p.dictionary.add("Pesto")
        // DataStore reads on its own IO thread, so virtual time does not wait for it: real time, with a limit.
        val start = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                var s = p.builder.build(whatsapp)
                while (s.profile != "note") {
                    delay(10)
                    s = p.builder.build(whatsapp)
                }
                s
            }
        }
        assertEquals("note", start.profile)
        assertEquals(listOf("Pesto"), start.terms)
    }

    @Test
    fun `fit keeps the terms in order while they fit`() {
        val big = "β".repeat(MAX_TERMS_CHARS - 3) // costs MAX - 2
        assertEquals(listOf(big, "α"), StartBuilder.fit(listOf(big, "α", "γ")))
        assertEquals(emptyList<String>(), StartBuilder.fit(listOf("δ".repeat(MAX_TERMS_CHARS))))
        assertEquals(listOf("x", "y"), StartBuilder.fit(listOf("x", "y")))
    }
}
