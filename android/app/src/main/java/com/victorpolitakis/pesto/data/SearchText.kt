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
    private val WORD = Regex("[\\p{L}\\p{N}\\p{M}]+")
    private const val SNIPPET_LENGTH = 120

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

    /**
     * The words of [text] that [query] finds (prefixes, after [normalize]), as ranges
     * of [text] itself: the index has no accents, the highlight is on the real text.
     */
    fun matches(text: String, query: String): List<IntRange> {
        val wanted = tokens(query)
        if (wanted.isEmpty()) return emptyList()
        return WORD.findAll(text)
            .filter { word -> normalize(word.value).let { w -> wanted.any { w.startsWith(it) } } }
            .map { it.range }
            .toList()
    }

    /**
     * About [length] characters of [text] on one line, around the first word found,
     * with the words found in it. Without a match: the start of the text.
     */
    fun snippet(text: String, query: String, length: Int = SNIPPET_LENGTH): Snippet {
        val line = buildString(text.length) { text.forEach { append(if (it.isWhitespace()) ' ' else it) } }
        val found = matches(line, query)
        val first = found.firstOrNull()
            ?: return Snippet(if (line.length > length) line.take(length) + "…" else line, emptyList())
        var start = (first.first - length / 3).coerceAtLeast(0)
        if (start > 0) {
            // Start at a word, not in the middle of one.
            val space = line.indexOf(' ', start)
            if (space in start until first.first) start = space + 1
        }
        val end = (start + length).coerceAtMost(line.length)
        val prefix = if (start > 0) "…" else ""
        val shift = prefix.length - start
        val inside = found.filter { it.first >= start && it.last < end }.map { (it.first + shift)..(it.last + shift) }
        return Snippet(prefix + line.substring(start, end) + (if (end < line.length) "…" else ""), inside)
    }
}
