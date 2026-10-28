package com.victorpolitakis.pesto.data

import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.protocol.Final
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A dictation to record. [final] is null when the connection broke and [lostText] is what was shown.
 * [requestedProfile] is the profile the phone asked for (C9): a fallback to the plain transcript
 * shows in the fallback reason, not in the profile. [startedAt] is the tap, wall clock.
 */
data class NewDictation(
    val createdAt: Long,
    val startedAt: Long,
    val context: FieldContext,
    val requestedProfile: String,
    val final: Final?,
    val lostText: String?,
    val insertMethod: String,
)

/** Where finished dictations go. An interface so the delivery can be tested without Room. */
interface DictationHistory {
    suspend fun record(dictation: NewDictation)
}

/** A deleted dictation, kept in memory for the undo of the snackbar (Δ15). */
class DeletedDictation internal constructor(internal val entity: DictationEntity)

/** The only way to the dictations table (PLAN.md: repositories). The stats read it too (StatsRepository). */
@Singleton
class HistoryRepository @Inject constructor(private val dao: DictationDao) : DictationHistory {
    override suspend fun record(dictation: NewDictation) {
        val f = dictation.final
        val c = dictation.context
        val rawText = f?.rawText ?: dictation.lostText.orEmpty()
        val text = f?.text ?: dictation.lostText.orEmpty()
        dao.insert(
            DictationEntity(
                createdAt = dictation.createdAt,
                packageName = c.packageName,
                inputType = c.inputType,
                hint = c.hint,
                singleLine = c.singleLine,
                rawText = rawText,
                text = text,
                profile = dictation.requestedProfile,
                fallbackReason = f?.fallbackReason,
                endReason = f?.endReason ?: "connection_lost",
                audioS = f?.stats?.audioS,
                words = f?.stats?.words,
                sttMs = f?.stats?.sttMs,
                llmMs = f?.stats?.llmMs,
                totalMs = f?.stats?.totalMs,
                insertMethod = dictation.insertMethod,
                searchText = SearchText.of(rawText, text),
                rawWords = SearchText.words(rawText),
                startedAt = dictation.startedAt,
            ),
        )
    }

    suspend fun count() = dao.count()

    val total: Flow<Int> get() = dao.total()

    /** Newest first, [limit] at a time (C8: 100, without Paging). */
    fun list(limit: Int, offset: Int = 0): Flow<List<DictationItem>> = dao.list(limit, offset)

    /**
     * Newest first, with the words found around the first of them. Nothing below
     * [SearchText.MIN_QUERY] characters. The query goes through the same
     * normalization as the index, and every word is a prefix.
     */
    fun search(query: String, limit: Int = SEARCH_LIMIT): Flow<List<SearchResult>> {
        val match = SearchText.ftsQuery(query) ?: return flowOf(emptyList())
        return dao.search(match, limit).map { rows -> rows.map { it.toResult(query) } }
    }

    suspend fun get(id: Long): DictationDetail? = dao.get(id)?.toDetail()

    /** Returns what [restore] needs to put it back, or null if it was not there. */
    suspend fun delete(id: Long): DeletedDictation? {
        val entity = dao.get(id) ?: return null
        dao.delete(id)
        return DeletedDictation(entity)
    }

    /** The same row, with the same id: the index takes it back through its trigger. */
    suspend fun restore(deleted: DeletedDictation) {
        dao.insert(deleted.entity)
    }

    /** Settings, «Διαγραφή όλων» (Δ15). */
    suspend fun deleteAll() = dao.deleteAll()

    private fun SearchRow.toResult(query: String): SearchResult {
        val inText = SearchText.snippet(text, query)
        // The search also reads the raw text: a word the profile changed is only there.
        val snippet = if (inText.highlights.isEmpty() && rawText != text) SearchText.snippet(rawText, query) else inText
        return SearchResult(DictationItem(id, packageName, createdAt, words), snippet)
    }

    private fun DictationEntity.toDetail() = DictationDetail(
        id = id,
        text = text,
        rawText = rawText,
        packageName = packageName,
        inputType = inputType,
        profile = profile,
        startedAt = startedAt,
        createdAt = createdAt,
        audioS = audioS,
        words = rawWords,
        outputWords = words,
        fallbackReason = fallbackReason,
        endReason = endReason,
        insertMethod = insertMethod,
        sttMs = sttMs,
        llmMs = llmMs,
        totalMs = totalMs,
    )

    companion object {
        const val PAGE = 100
        const val SEARCH_LIMIT = 100
    }
}
