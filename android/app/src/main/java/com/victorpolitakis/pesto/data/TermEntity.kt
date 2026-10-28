package com.victorpolitakis.pesto.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Dictionary term (C2), sent to the speech recognition with every dictation.
 * [termKey] is [SearchText.normalize] of the term, unique, so «Πολιτάκης» and
 * «πολιτακης» are the same term: SQLite's NOCASE folds only ASCII.
 */
@Entity(tableName = "terms", indices = [Index(value = ["term_key"], unique = true)])
data class TermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    @ColumnInfo(name = "term_key") val termKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
