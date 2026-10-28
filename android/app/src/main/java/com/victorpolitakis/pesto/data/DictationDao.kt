package com.victorpolitakis.pesto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A search hit, with both texts for the snippet. */
data class SearchRow(
    val id: Long,
    val packageName: String?,
    val createdAt: Long,
    val words: Int,
    val rawText: String,
    val text: String,
)

/** The sums behind [Totals]. */
data class TotalsRow(
    val dictations: Int,
    val dictationsToday: Int,
    val words: Int,
    val wordsToday: Int,
    val wordsThisWeek: Int,
    val audioSeconds: Double,
    val wordsWithAudio: Int,
    val minutesSaved: Double,
)

/** When a dictation ended and how many words were said, for the bars per day. */
data class TimeWords(val createdAt: Long, val words: Int)

/** The column aliases are the field names of the classes the queries return. */
@Dao
interface DictationDao {
    @Insert suspend fun insert(entity: DictationEntity): Long

    @Query("SELECT COUNT(*) FROM dictations") suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM dictations") fun total(): Flow<Int>

    @Query(
        "SELECT id, package AS packageName, created_at AS createdAt, raw_words AS words FROM dictations " +
            "ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
    )
    fun list(limit: Int, offset: Int): Flow<List<DictationItem>>

    /** [match]: an FTS4 MATCH expression from [SearchText.ftsQuery]. Newest first. */
    @Query(
        "SELECT id, package AS packageName, created_at AS createdAt, raw_words AS words, raw_text AS rawText, text " +
            "FROM dictations WHERE id IN (SELECT rowid FROM dictations_fts WHERE dictations_fts MATCH :match) " +
            "ORDER BY created_at DESC, id DESC LIMIT :limit",
    )
    fun search(match: String, limit: Int): Flow<List<SearchRow>>

    @Query("SELECT * FROM dictations WHERE id = :id") suspend fun get(id: Long): DictationEntity?

    @Query("DELETE FROM dictations WHERE id = :id") suspend fun delete(id: Long)

    @Query("DELETE FROM dictations") suspend fun deleteAll()

    /**
     * Δ8: words = raw_words. Minutes saved = Σ max(0, words / 40 − audio_s / 60): typing at
     * 40 words per minute (StatsRepository.TYPING_WPM) against speaking. Without audio_s (a
     * broken connection) a dictation counts in the words and the uses, not in WPM or minutes.
     */
    @Query(
        "SELECT COUNT(*) AS dictations, " +
            "COALESCE(SUM(CASE WHEN created_at >= :today THEN 1 ELSE 0 END), 0) AS dictationsToday, " +
            "COALESCE(SUM(raw_words), 0) AS words, " +
            "COALESCE(SUM(CASE WHEN created_at >= :today THEN raw_words ELSE 0 END), 0) AS wordsToday, " +
            "COALESCE(SUM(CASE WHEN created_at >= :week THEN raw_words ELSE 0 END), 0) AS wordsThisWeek, " +
            "COALESCE(SUM(audio_s), 0.0) AS audioSeconds, " +
            "COALESCE(SUM(CASE WHEN audio_s IS NOT NULL THEN raw_words ELSE 0 END), 0) AS wordsWithAudio, " +
            "COALESCE(SUM(CASE WHEN audio_s IS NOT NULL THEN MAX(0, raw_words / 40.0 - audio_s / 60.0) ELSE 0 END), 0.0) AS minutesSaved " +
            "FROM dictations",
    )
    fun totals(today: Long, week: Long): Flow<TotalsRow>

    @Query("SELECT created_at AS createdAt, raw_words AS words FROM dictations WHERE created_at >= :since")
    fun since(since: Long): Flow<List<TimeWords>>

    /** The apps of the History, the most used first. */
    @Query(
        "SELECT package AS packageName, COUNT(*) AS dictations, MAX(created_at) AS lastUsed FROM dictations " +
            "WHERE package IS NOT NULL GROUP BY package ORDER BY COUNT(*) DESC, MAX(created_at) DESC",
    )
    fun apps(): Flow<List<AppUsage>>

    // For the data check of phase 4: where something fails (Analytics, «Αξιοπιστία»).

    @Query("SELECT profile AS value, COUNT(*) AS dictations FROM dictations GROUP BY profile ORDER BY COUNT(*) DESC")
    fun byProfile(): Flow<List<KeyCount>>

    @Query(
        "SELECT fallback_reason AS value, COUNT(*) AS dictations FROM dictations GROUP BY fallback_reason ORDER BY COUNT(*) DESC",
    )
    fun byFallbackReason(): Flow<List<KeyCount>>

    @Query("SELECT end_reason AS value, COUNT(*) AS dictations FROM dictations GROUP BY end_reason ORDER BY COUNT(*) DESC")
    fun byEndReason(): Flow<List<KeyCount>>

    @Query("SELECT insert_method AS value, COUNT(*) AS dictations FROM dictations GROUP BY insert_method ORDER BY COUNT(*) DESC")
    fun byInsertMethod(): Flow<List<KeyCount>>
}
