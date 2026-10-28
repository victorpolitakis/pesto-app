package com.victorpolitakis.pesto.insert

/**
 * C5: how dictated text joins the text already in the field. Pure Kotlin.
 * Returns the exact piece to insert at the cursor, with the spaces it needs.
 */
object TextJoiner {
    private const val NO_SPACE_AFTER = "([{«\"'“‘"
    private const val NO_SPACE_BEFORE = ".,;:!?…)]}»"
    private const val SENTENCE_END = ".!?…"

    /** [terms]: the Dictionary terms of this dictation (C2), which keep their case mid-sentence. */
    fun join(before: String, after: String, dictated: String, terms: Set<String> = emptySet()): String {
        val text = dictated.trim()
        if (text.isEmpty()) return ""
        val spaceBefore = before.isNotEmpty() &&
            !before.last().isWhitespace() &&
            before.last() !in NO_SPACE_AFTER &&
            text.first() !in NO_SPACE_BEFORE
        val spaceAfter = after.isNotEmpty() && after.first().isLetterOrDigit()
        val cased = if (text.first().isLetter()) recase(text, sentenceStart(before), terms) else text
        return (if (spaceBefore) " " else "") + cased + (if (spaceAfter) " " else "")
    }

    /** At the start of the field, after a new line, or after the end of a sentence. */
    fun sentenceStart(before: String): Boolean {
        if (before.isEmpty() || before.last() == '\n') return true
        val trimmed = before.trimEnd(' ', '\t')
        if (trimmed.isEmpty() || trimmed.last() == '\n') return true
        val last = trimmed.last()
        // ";" is the Greek question mark, but a semicolon in English.
        return last in SENTENCE_END || (last == ';' && isMostlyGreek(trimmed))
    }

    /**
     * Capital only at the start of a sentence (Δ6). Soniox capitalises the
     * first word of every dictation, which is wrong mid-sentence. Acronyms
     * ("API"), words with a capital inside ("iPhone", "McDonald") and the
     * Dictionary terms («Πολιτάκης», also of more words) are kept.
     */
    private fun recase(text: String, start: Boolean, terms: Set<String>): String {
        val first = text.first()
        if (start) return first.uppercaseChar() + text.substring(1)
        val word = text.takeWhile { it.isLetter() }
        val acronym = word.length > 1 && word.all { it.isUpperCase() }
        val innerCapital = word.drop(1).any { it.isUpperCase() }
        return if (acronym || innerCapital || startsWithTerm(text, terms)) text else first.lowercaseChar() + text.substring(1)
    }

    /** The text starts with a whole term, as it is written in the Dictionary. */
    private fun startsWithTerm(text: String, terms: Set<String>): Boolean = terms.any { term ->
        term.isNotEmpty() && text.startsWith(term) && (text.length == term.length || !text[term.length].isLetterOrDigit())
    }

    private fun isMostlyGreek(text: String): Boolean {
        val greek = text.count { it in 'Ͱ'..'Ͽ' || it in 'ἀ'..'῿' }
        val latin = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        return greek > latin
    }
}
