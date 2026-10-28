package com.victorpolitakis.pesto.data

import java.time.LocalDate

/*
 * What the repositories return: plain data, never a Room entity, so the
 * screens can be previewed and tested with made-up values.
 */

/** One row of the History, Search and Home lists. [words]: the words said (raw_words, Δ8). */
data class DictationItem(val id: Long, val packageName: String?, val createdAt: Long, val words: Int)

/** A piece of a text, with the words the search found in it as ranges of [text]. */
data class Snippet(val text: String, val highlights: List<IntRange>)

data class SearchResult(val item: DictationItem, val snippet: Snippet)

/** Everything recorded about one dictation: the History detail, and the data check of phase 4. */
data class DictationDetail(
    val id: Long,
    val text: String,
    val rawText: String,
    val packageName: String?,
    val inputType: String?,
    val profile: String,
    val startedAt: Long,
    val createdAt: Long,
    val audioS: Double?,
    /** The words said (raw_words). */
    val words: Int,
    /** The words of the text that went in, as the backend counts them. Null for a broken connection. */
    val outputWords: Int?,
    val fallbackReason: String?,
    val endReason: String,
    val insertMethod: String,
    val sttMs: Int?,
    val llmMs: Int?,
    val totalMs: Int?,
) {
    /** Words per minute of speech. Null without audio (a broken connection). */
    val wpm: Double? get() = audioS?.takeIf { it > 0 }?.let { words / (it / 60) }
}

/** The totals of Home and Analytics (Δ8, Δ21). [wpm] is null while no dictation has audio. */
data class Totals(
    val dictations: Int = 0,
    val dictationsToday: Int = 0,
    val words: Int = 0,
    val wordsToday: Int = 0,
    val wordsThisWeek: Int = 0,
    val speechMinutes: Double = 0.0,
    val minutesSaved: Double = 0.0,
    val wpm: Double? = null,
)

/** One day of the bars of Analytics. */
data class DayCount(val day: LocalDate, val dictations: Int, val words: Int)

/** One app of the History: how many dictations, and when the last one ended. */
data class AppUsage(val packageName: String, val dictations: Int, val lastUsed: Long)

/** How many dictations have one value of a column (a profile, a fallback reason…). [value] null: none. */
data class KeyCount(val value: String?, val dictations: Int)

/** A Dictionary term. */
data class Term(val id: Long, val term: String)
