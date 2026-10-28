package com.victorpolitakis.pesto.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One dictation in the History (C8 screens in phase 3, FTS added there with a
 * migration). Everything of the backend's `final`, plus the app and the field.
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
    val profile: String,
    @ColumnInfo(name = "fallback_reason") val fallbackReason: String?,
    /** `user`, `silence`, … from the backend, or `connection_lost` (Δ14). */
    @ColumnInfo(name = "end_reason") val endReason: String,
    @ColumnInfo(name = "audio_s") val audioS: Double?,
    val words: Int?,
    @ColumnInfo(name = "stt_ms") val sttMs: Int?,
    @ColumnInfo(name = "llm_ms") val llmMs: Int?,
    @ColumnInfo(name = "total_ms") val totalMs: Int?,
    /** COMMIT_TEXT, SET_TEXT or CLIPBOARD: which apps need X1. */
    @ColumnInfo(name = "insert_method") val insertMethod: String,
)
