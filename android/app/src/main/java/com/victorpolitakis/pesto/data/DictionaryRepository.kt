package com.victorpolitakis.pesto.data

import android.database.sqlite.SQLiteConstraintException
import com.victorpolitakis.pesto.protocol.MAX_TERMS_CHARS
import com.victorpolitakis.pesto.util.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Why a term was not saved, or [OK]. */
enum class TermResult { OK, EMPTY, DUPLICATE, TOO_LONG }

/**
 * The Dictionary terms (C2): one place for them, with the checks for
 * duplicates and for the backend's limit, so the screen never saves what the
 * start message could not carry. All of them go to the speech recognition
 * with every dictation (StartBuilder), never to the profile's prompt (Δ6).
 * TODO(X4): addAll(terms), for terms from a photo, after a confirmation screen (third-party text: spotlighting).
 */
@Singleton
class DictionaryRepository @Inject constructor(private val dao: TermDao, private val clock: WallClock) {
    val terms: Flow<List<Term>> = dao.all().map { rows -> rows.map { Term(it.id, it.term) } }

    suspend fun add(term: String): TermResult {
        val t = term.trim()
        if (t.isEmpty()) return TermResult.EMPTY
        val key = SearchText.normalize(t)
        val all = dao.list()
        if (all.any { it.termKey == key }) return TermResult.DUPLICATE
        if (size(all.map { it.term }) + cost(t) > MAX_TERMS_CHARS) return TermResult.TOO_LONG
        return saved { dao.insert(TermEntity(term = t, termKey = key, createdAt = clock.now())) }
    }

    suspend fun update(id: Long, term: String): TermResult {
        val t = term.trim()
        if (t.isEmpty()) return TermResult.EMPTY
        val key = SearchText.normalize(t)
        val all = dao.list()
        val current = all.firstOrNull { it.id == id } ?: return add(t) // deleted meanwhile
        if (all.any { it.id != id && it.termKey == key }) return TermResult.DUPLICATE
        if (size(all.filter { it.id != id }.map { it.term }) + cost(t) > MAX_TERMS_CHARS) return TermResult.TOO_LONG
        return saved { dao.update(current.copy(term = t, termKey = key)) }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** The unique key catches a duplicate added at the same moment from elsewhere. */
    private inline fun saved(write: () -> Unit): TermResult = try {
        write()
        TermResult.OK
    } catch (e: SQLiteConstraintException) {
        TermResult.DUPLICATE
    }

    companion object {
        /** What one term costs in the backend's limit: its characters (code points, like Python's len) and a separator. */
        fun cost(term: String): Int = term.codePointCount(0, term.length) + 1

        fun size(terms: List<String>): Int = terms.sumOf { cost(it) }

        /** How many characters are left for new terms. */
        fun remaining(terms: List<String>): Int = MAX_TERMS_CHARS - size(terms)
    }
}
