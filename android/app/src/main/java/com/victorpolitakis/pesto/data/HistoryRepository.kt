package com.victorpolitakis.pesto.data

import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.protocol.Final
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

/** The only way to the dictations table (PLAN.md: repositories). */
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
}
