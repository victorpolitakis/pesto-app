package com.victorpolitakis.pesto.keyboard

/**
 * Builds the `pesto_locales.txt` the language-seed patch bundles (RESEARCH §10, spike S2). Pure
 * logic, so it is unit-tested on the JVM; the Android reader (KeyboardLocaleReader) turns the stock
 * Gboard's enabled subtypes into [Locale]s and calls this.
 *
 * The three keys, and their format, are Gboard's own, and must match the bridge's LocaleSeeder and
 * gboard-patches' PestoLocales. The values are written as built here, with no later normalization.
 */
object KeyboardLocales {
    const val KEY_ENABLED_ENTRIES = "enabled_input_method_entries"
    const val KEY_CURRENT_ENTRY = "current_input_method_entry"
    const val KEY_ENABLED_LANGUAGE_TAGS = "enabled_ime_language_tags"

    /** One enabled keyboard language: the BCP-47 tag Gboard reports, and its layout if known. */
    data class Locale(val languageTag: String, val layout: String?)

    /**
     * Default layout per language, for a subtype that exposes no `PkLayoutType` (S2: present for
     * English, empty for Greek). Keyed by the primary subtag, so `el-GR` and `en-GB` resolve.
     * Extend as needed; an unknown language keeps a null layout and Gboard picks one.
     */
    private val DEFAULT_LAYOUT_BY_LANGUAGE = mapOf(
        "el" to "greek",
        "en" to "qwerty",
    )

    fun primaryLanguage(languageTag: String): String = languageTag.substringBefore('-').lowercase()

    fun defaultLayout(languageTag: String): String? = DEFAULT_LAYOUT_BY_LANGUAGE[primaryLanguage(languageTag)]

    private fun resolve(locale: Locale): Locale =
        if (locale.layout != null) locale else locale.copy(layout = defaultLayout(locale.languageTag))

    private fun entry(locale: Locale): String =
        resolve(locale).let { if (it.layout != null) "${it.languageTag}:${it.layout}" else it.languageTag }

    /**
     * The `pesto_locales.txt` contents for the enabled [locales] (the first is the current one), or
     * null when there is nothing to seed. [languageTags] is `enabled_ime_language_tags`, taken as
     * given (TODO(S2): confirm on the POCO which tags Gboard stores).
     */
    fun fileContents(
        locales: List<Locale>,
        languageTags: List<String> = locales.map { it.languageTag },
    ): String? {
        if (locales.isEmpty()) return null
        val lines = listOf(
            "$KEY_ENABLED_ENTRIES=${locales.joinToString(";", transform = ::entry)}",
            "$KEY_CURRENT_ENTRY=${entry(locales.first())}",
            "$KEY_ENABLED_LANGUAGE_TAGS=${languageTags.joinToString(";")}",
        )
        return lines.joinToString("\n") + "\n"
    }
}
