package com.victorpolitakis.pesto.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One dictation in the History (C8). Everything of the backend's `final`, plus
 * the app and the field. The last three columns came with v2 (phase 3): their
 * defaults are the ones of the migration, which Room checks after it.
 */
@Entity(tableName = "dictations")
data class DictationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "package") val packageName: String?,
    @ColumnInfo(name = "input_type") val inputType: String?,
    val hint: String?,
    @ColumnInfo(name = "single_line") val singleLine: Boolean?,
    @ColumnInfo(name = "raw_text") val rawText: String,
    val text: String,
    /** The profile the phone asked for (C9), also when the connection broke. */
    val profile: String,
    @ColumnInfo(name = "fallback_reason") val fallbackReason: String?,
    /** `user`, `silence`, … from the backend, or `connection_lost` (Δ14). */
    @ColumnInfo(name = "end_reason") val endReason: String,
    @ColumnInfo(name = "audio_s") val audioS: Double?,
    /** Words of the output, as the backend counts them (after the profile). */
    val words: Int?,
    @ColumnInfo(name = "stt_ms") val sttMs: Int?,
    @ColumnInfo(name = "llm_ms") val llmMs: Int?,
    @ColumnInfo(name = "total_ms") val totalMs: Int?,
    /** COMMIT_TEXT, SET_TEXT or CLIPBOARD: which apps need X1. */
    @ColumnInfo(name = "insert_method") val insertMethod: String,
    /** [SearchText.of] the two texts: what the search index reads (Δ9). */
    @ColumnInfo(name = "search_text", defaultValue = "''") val searchText: String,
    /** Words of [rawText], what was said: the words, WPM and minutes saved of the stats (Δ8). */
    @ColumnInfo(name = "raw_words", defaultValue = "0") val rawWords: Int,
    /** The tap, wall clock. For the rows from before v2: [createdAt]. */
    @ColumnInfo(name = "started_at", defaultValue = "0") val startedAt: Long,
)
