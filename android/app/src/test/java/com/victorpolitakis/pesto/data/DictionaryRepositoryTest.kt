package com.victorpolitakis.pesto.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.protocol.MAX_TERMS_CHARS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** C2: the terms, their duplicates and the backend's limit, on the real table (its unique key). */
@RunWith(AndroidJUnit4::class)
class DictionaryRepositoryTest {
    private val db = inMemoryDatabase()
    private val dictionary = DictionaryRepository(db.terms(), clock = { NOW })

    @After fun close() = db.close()

    private suspend fun terms() = dictionary.terms.first().map { it.term }

    @Test
    fun `add, list in order, edit and delete`() = runTest {
        assertEquals(TermResult.OK, dictionary.add("  Πολιτάκης "))
        assertEquals(TermResult.OK, dictionary.add("Pesto"))
        assertEquals(listOf("Pesto", "Πολιτάκης"), terms())

        val pesto = dictionary.terms.first().first { it.term == "Pesto" }.id
        assertEquals(TermResult.OK, dictionary.update(pesto, "PESTO"))
        assertEquals(listOf("PESTO", "Πολιτάκης"), terms())

        dictionary.delete(pesto)
        assertEquals(listOf("Πολιτάκης"), terms())
    }

    @Test
    fun `the same term with other accents or case is a duplicate`() = runTest {
        dictionary.add("Πολιτάκης")
        assertEquals(TermResult.DUPLICATE, dictionary.add("πολιτακης"))
        assertEquals(TermResult.DUPLICATE, dictionary.add("ΠΟΛΙΤΑΚΗΣ"))
        assertEquals(TermResult.EMPTY, dictionary.add("   "))

        dictionary.add("Pesto")
        val pesto = dictionary.terms.first().first { it.term == "Pesto" }.id
        assertEquals(TermResult.DUPLICATE, dictionary.update(pesto, "Πολιτάκης"))
        assertEquals(listOf("Pesto", "Πολιτάκης"), terms())
    }

    @Test
    fun `the limit of the backend cannot be passed`() = runTest {
        val big = "α".repeat(MAX_TERMS_CHARS - 2) // costs MAX_TERMS_CHARS - 1
        assertEquals(TermResult.OK, dictionary.add(big))
        assertEquals(1, DictionaryRepository.remaining(terms()))
        assertEquals(TermResult.TOO_LONG, dictionary.add("β"))
        assertEquals(TermResult.TOO_LONG, dictionary.add("γ".repeat(MAX_TERMS_CHARS)))
        assertEquals(listOf(big), terms())
    }

    @Test
    fun `an edit counts without the old text of the term`() = runTest {
        dictionary.add("α".repeat(MAX_TERMS_CHARS - 2))
        val id = dictionary.terms.first().single().id
        assertEquals(TermResult.OK, dictionary.update(id, "β".repeat(MAX_TERMS_CHARS - 1)))
        assertEquals(TermResult.TOO_LONG, dictionary.update(id, "β".repeat(MAX_TERMS_CHARS)))
    }

    @Test
    fun `a pasted term is saved on one line`() = runTest {
        assertEquals(TermResult.OK, dictionary.add(" IBM\nApp \t Connect "))
        assertEquals(listOf("IBM App Connect"), terms())
        assertEquals(TermResult.DUPLICATE, dictionary.add("ibm app connect"))
    }

    @Test
    fun `a term costs like in the backend`() {
        assertEquals(10, DictionaryRepository.cost("Πολιτάκης"))
        // Python counts code points: an emoji is one character there.
        assertEquals(3, DictionaryRepository.cost("😀a"))
        assertEquals(MAX_TERMS_CHARS - 13, DictionaryRepository.remaining(listOf("Πολιτάκης", "AB")))
    }
}
