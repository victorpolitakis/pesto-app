package com.victorpolitakis.pesto.keyboard

import com.victorpolitakis.pesto.keyboard.KeyboardLocales.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pesto_locales.txt the patcher bundles, built from the stock keyboard's languages. No APK. */
class KeyboardLocalesTest {
    @Test
    fun `the S2 languages give exactly what Gboard wrote`() {
        val contents = KeyboardLocales.fileContents(
            listOf(Locale("en-GB", "qwerty"), Locale("el-GR", null)),
            languageTags = listOf("en-GB", "el"),
        )
        assertEquals(
            """
            |enabled_input_method_entries=en-GB:qwerty;el-GR:greek
            |current_input_method_entry=en-GB:qwerty
            |enabled_ime_language_tags=en-GB;el
            |
            """.trimMargin(),
            contents,
        )
    }

    @Test
    fun `a missing layout falls back to the default table`() {
        assertEquals("greek", KeyboardLocales.defaultLayout("el-GR"))
        assertEquals("qwerty", KeyboardLocales.defaultLayout("en-US"))
        assertNull(KeyboardLocales.defaultLayout("fr-FR"))
    }

    @Test
    fun `a language with no known layout contributes only its tag`() {
        val contents = KeyboardLocales.fileContents(listOf(Locale("fr-FR", null)))
        assertEquals(
            "enabled_input_method_entries=fr-FR\ncurrent_input_method_entry=fr-FR\nenabled_ime_language_tags=fr-FR\n",
            contents,
        )
    }

    @Test
    fun `an explicit layout is kept over the default`() {
        val contents = KeyboardLocales.fileContents(listOf(Locale("el-GR", "polytonic")), languageTags = listOf("el"))
        assertEquals(
            "enabled_input_method_entries=el-GR:polytonic\ncurrent_input_method_entry=el-GR:polytonic\nenabled_ime_language_tags=el\n",
            contents,
        )
    }

    @Test
    fun `no languages means nothing to seed`() {
        assertNull(KeyboardLocales.fileContents(emptyList()))
    }
}
