package com.victorpolitakis.pesto.data

import java.text.Normalizer

/**
 * Search in Greek (Δ9). SQLite's unicode61 removes accents only from Latin
 * letters, so «καλημερα» would not find «Καλημέρα». The index and the query go
 * through the same [normalize] here instead. Pure Kotlin, no Android.
 */
object SearchText {
    private val MARKS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** The shortest query worth searching for, in letters and digits. */
    const val MIN_QUERY = 2

    /** No accents or diaeresis (any combining mark), lower case, final ς as σ. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace('ς', 'σ')

    /** What the index keeps for one dictation: both texts, once when they are the same. */
    fun of(rawText: String, text: String): String = normalize(if (text == rawText) rawText else "$rawText $text")

    /** Runs of non-space characters, like the backend's `len(text.split())`. */
    fun words(text: String): Int {
        var count = 0
        var inWord = false
        for (c in text) {
            if (c.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    /**
     * The FTS MATCH expression for what the user typed: every word is a prefix
     * («καλημ*»), so it finds while typing. Letters and digits only, so nothing
     * is read as FTS syntax. Null below [MIN_QUERY] characters.
     */
    fun ftsQuery(input: String): String? {
        val words = tokens(input)
        if (words.sumOf { it.length } < MIN_QUERY) return null
        return words.joinToString(" ") { "$it*" }
    }

    /** The normalized words of [text]. */
    fun tokens(text: String): List<String> = normalize(text).split(NOT_WORD).filter { it.isNotEmpty() }
}
