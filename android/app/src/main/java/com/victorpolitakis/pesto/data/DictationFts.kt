package com.victorpolitakis.pesto.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * The search index of the History (C8, Δ9): FTS4 over `search_text`, with the
 * dictations table as its content, so no text is stored twice. Room keeps it
 * in sync with triggers; the migration creates the same ones (Migrations.kt).
 */
@Fts4(contentEntity = DictationEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "dictations_fts")
data class DictationFts(@ColumnInfo(name = "search_text") val searchText: String)
