package com.victorpolitakis.pesto.gboard

/**
 * The shape of the `assets/pesto_locales.txt` file that the language-seed patch reads, and the
 * rules for turning Gboard's language subtypes into the three preference keys Gboard stores
 * (RESEARCH §10, spike S2).
 *
 * This is pure logic with no patcher or Android dependency, so it is unit-tested on the JVM without
 * a Gboard APK. The on-device patcher (phase 3) produces the file from the stock keyboard's enabled
 * subtypes before they are disabled; the extension consumes it at the keyboard's first start.
 *
 * The preference keys, as read from Gboard's `shared_prefs` in S2:
 * - `enabled_input_method_entries`  e.g. `en-GB:qwerty;el-GR:greek`
 * - `current_input_method_entry`    e.g. `en-GB:qwerty`
 * - `enabled_ime_language_tags`     e.g. `en-GB;el`
 */
object PestoLocales {
    const val KEY_ENABLED_ENTRIES = "enabled_input_method_entries"
    const val KEY_CURRENT_ENTRY = "current_input_method_entry"
    const val KEY_ENABLED_LANGUAGE_TAGS = "enabled_ime_language_tags"
    const val KEY_SEEDED = "pesto_seeded"

    /**
     * A single enabled keyboard language: the BCP-47 tag Gboard reports for the subtype, and the
     * keyboard layout. [layout] is null when the stock subtype carried no `PkLayoutType` and no
     * default is known, in which case the layout is left to Gboard.
     */
    data class Locale(val languageTag: String, val layout: String?)

    /**
     * Default keyboard layout per language, used when a stock subtype exposes no `PkLayoutType`
     * (S2: present for English `qwerty`, empty for Greek). Keyed by the primary language subtag so
     * that regional variants such as `el-GR` and `en-GB` resolve the same. Extend as needed; an
     * unknown language returns null and the layout is left to Gboard.
     */
    private val DEFAULT_LAYOUT_BY_LANGUAGE = mapOf(
        "el" to "greek",
        "en" to "qwerty",
    )

    /** The primary language subtag of a BCP-47 tag, lowercased: `el-GR` -> `el`, `en-GB` -> `en`. */
    fun primaryLanguage(languageTag: String): String =
        languageTag.substringBefore('-').lowercase()

    /** The default layout for a language tag, or null when none is known. */
    fun defaultLayout(languageTag: String): String? =
        DEFAULT_LAYOUT_BY_LANGUAGE[primaryLanguage(languageTag)]

    /**
     * Resolves a locale's layout: the layout it already carries, otherwise the default for its
     * language. The result may still have a null layout when neither is known.
     */
    fun resolve(locale: Locale): Locale =
        if (locale.layout != null) locale else locale.copy(layout = defaultLayout(locale.languageTag))

    /**
     * The `enabled_input_method_entries` value: `tag:layout` pairs joined by `;`. A locale with no
     * resolved layout contributes just its tag, letting Gboard pick the layout.
     */
    fun enabledEntries(locales: List<Locale>): String =
        locales.joinToString(";") { entry(resolve(it)) }

    private fun entry(locale: Locale): String =
        if (locale.layout != null) "${locale.languageTag}:${locale.layout}" else locale.languageTag

    /**
     * Builds the three preference values from the enabled locales. The first locale is the current
     * one. [languageTags] is the `enabled_ime_language_tags` value, taken as given; it defaults to
     * the locales' own tags. There is no rule of ours that turns one into the other.
     *
     * TODO(S2): S2 saw Gboard store `en-GB;el` for subtypes reported as `en-GB`, `el-GR`. Check on
     * the POCO which tags to pass here (phase 3), against what Gboard itself writes.
     */
    fun buildPreferences(
        locales: List<Locale>,
        languageTags: List<String> = locales.map { it.languageTag },
    ): Map<String, String> {
        require(locales.isNotEmpty()) { "at least one locale is required" }
        val resolved = locales.map(::resolve)
        return linkedMapOf(
            KEY_ENABLED_ENTRIES to resolved.joinToString(";") { entry(it) },
            KEY_CURRENT_ENTRY to entry(resolved.first()),
            KEY_ENABLED_LANGUAGE_TAGS to languageTags.joinToString(";"),
        )
    }

    /**
     * Serializes preferences to the `key=value` line format of `pesto_locales.txt`. Keys and values
     * contain neither `=` nor newlines in practice, so no escaping is needed.
     */
    fun serialize(preferences: Map<String, String>): String =
        preferences.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n"

    /**
     * Parses the `key=value` lines of `pesto_locales.txt`. Blank lines and lines without `=` are
     * ignored; the key is everything before the first `=`.
     */
    fun parse(text: String): Map<String, String> =
        text.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || '=' !in trimmed) null
                else trimmed.substringBefore('=') to trimmed.substringAfter('=')
            }
            .toMap()
}
