package com.victorpolitakis.pesto.gboard

import com.victorpolitakis.pesto.gboard.PestoLocales.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for the pure locale logic. These need no Gboard APK and always run.
 */
class PestoLocalesTest {
    @Test
    fun `default layout is known for english and greek, unknown otherwise`() {
        assertEquals("qwerty", PestoLocales.defaultLayout("en-GB"))
        assertEquals("qwerty", PestoLocales.defaultLayout("en-US"))
        assertEquals("greek", PestoLocales.defaultLayout("el-GR"))
        assertEquals("greek", PestoLocales.defaultLayout("el"))
        assertNull(PestoLocales.defaultLayout("fr-FR"))
    }

    @Test
    fun `primary language drops the region`() {
        assertEquals("el", PestoLocales.primaryLanguage("el-GR"))
        assertEquals("en", PestoLocales.primaryLanguage("en-GB"))
        assertEquals("de", PestoLocales.primaryLanguage("DE"))
    }

    @Test
    fun `resolve fills in a missing layout from the default table`() {
        assertEquals(Locale("el-GR", "greek"), PestoLocales.resolve(Locale("el-GR", null)))
        // An explicit layout is kept.
        assertEquals(Locale("el-GR", "polytonic"), PestoLocales.resolve(Locale("el-GR", "polytonic")))
        // Unknown language with no layout stays null.
        assertEquals(Locale("fr-FR", null), PestoLocales.resolve(Locale("fr-FR", null)))
    }

    @Test
    fun `enabled entries join tag and layout, matching the S2 observation`() {
        val locales = listOf(Locale("en-GB", "qwerty"), Locale("el-GR", null))
        assertEquals("en-GB:qwerty;el-GR:greek", PestoLocales.enabledEntries(locales))
    }

    @Test
    fun `entry without a resolvable layout contributes only the tag`() {
        assertEquals("fr-FR", PestoLocales.enabledEntries(listOf(Locale("fr-FR", null))))
    }

    @Test
    fun `buildPreferences produces the three keys`() {
        val prefs = PestoLocales.buildPreferences(
            listOf(Locale("en-GB", "qwerty"), Locale("el-GR", null)),
            languageTags = listOf("en-GB", "el"),
        )
        assertEquals("en-GB:qwerty;el-GR:greek", prefs[PestoLocales.KEY_ENABLED_ENTRIES])
        assertEquals("en-GB:qwerty", prefs[PestoLocales.KEY_CURRENT_ENTRY])
        assertEquals("en-GB;el", prefs[PestoLocales.KEY_ENABLED_LANGUAGE_TAGS])
    }

    @Test
    fun `serialize and parse round-trip`() {
        val prefs = PestoLocales.buildPreferences(listOf(Locale("en-GB", "qwerty"), Locale("el-GR", null)))
        val text = PestoLocales.serialize(prefs)
        assertEquals(prefs, PestoLocales.parse(text))
    }

    @Test
    fun `example asset holds the values Gboard wrote in S2, as given`() {
        assertEquals(
            mapOf(
                PestoLocales.KEY_ENABLED_ENTRIES to "en-GB:qwerty;el-GR:greek",
                PestoLocales.KEY_CURRENT_ENTRY to "en-GB:qwerty",
                PestoLocales.KEY_ENABLED_LANGUAGE_TAGS to "en-GB;el",
            ),
            PestoLocales.parse(EXAMPLE_PESTO_LOCALES),
        )
    }

    @Test
    fun `parse ignores blank and malformed lines`() {
        val text = """
            |enabled_ime_language_tags=en-GB;el
            |
            |garbage-without-equals
            |current_input_method_entry=en-GB:qwerty
        """.trimMargin()
        val parsed = PestoLocales.parse(text)
        assertEquals(2, parsed.size)
        assertEquals("en-GB;el", parsed["enabled_ime_language_tags"])
        assertEquals("en-GB:qwerty", parsed["current_input_method_entry"])
    }
}
