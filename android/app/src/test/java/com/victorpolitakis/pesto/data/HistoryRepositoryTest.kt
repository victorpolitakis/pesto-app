package com.victorpolitakis.pesto.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.FakeLog
import com.victorpolitakis.pesto.dictation.Outcome
import com.victorpolitakis.pesto.dictation.OutcomeDelivery
import com.victorpolitakis.pesto.insert.InsertMethod
import com.victorpolitakis.pesto.insert.Inserter
import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Start
import com.victorpolitakis.pesto.protocol.Stats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/** Step 3: from the record to the search, the stats and the deletion, on the real database. */
@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {
    private val db = inMemoryDatabase()
    private val history = HistoryRepository(db.dictations())
    private val stats = StatsRepository(db.dictations(), clock = { NOW }, zone = { ZoneOffset.UTC })

    @After fun close() = db.close()

    @Test
    fun `record, search, stats, delete and undo, from one end to the other`() = runTest {
        history.record(newDictation("καλημέρα γιώργο", "Καλημέρα Γιώργο.", createdAt = NOW - 2_000))
        history.record(newDictation("στείλε το αρχείο", "Στείλε το αρχείο.", packageName = "com.google.android.gm", createdAt = NOW - 1_000))
        assertEquals(2, stats.totals().first().dictations)
        assertEquals(5, stats.totals().first().words)

        val found = history.search("ΚΑΛΗΜΕΡΑ").first()
        assertEquals(1, found.size)
        val id = found.single().item.id
        assertEquals("Καλημέρα Γιώργο.", history.get(id)?.text)

        val deleted = history.delete(id)!!
        assertEquals(emptyList<SearchResult>(), history.search("καλημερα").first())
        assertEquals(1, stats.totals().first().dictations)

        history.restore(deleted)
        assertEquals(listOf(id), history.search("καλημερα").first().map { it.item.id })
        assertEquals(2, stats.totals().first().dictations)

        history.deleteAll()
        assertEquals(0, stats.totals().first().dictations)
        assertEquals(emptyList<DictationItem>(), history.list(100).first())
        assertEquals(emptyList<SearchResult>(), history.search("αρχείο").first())
    }

    @Test
    fun `the list is newest first, a page at a time`() = runTest {
        repeat(5) { history.record(newDictation("λέξη $it", createdAt = NOW + it)) }
        assertEquals(listOf(NOW + 4, NOW + 3), history.list(limit = 2).first().map { it.createdAt })
        assertEquals(listOf(NOW + 2, NOW + 1), history.list(limit = 2, offset = 2).first().map { it.createdAt })
        assertEquals(5, history.total.first())
    }

    @Test
    fun `a row has the app, the end and the words said`() = runTest {
        history.record(newDictation("εεε καλημέρα σε όλους", "Καλημέρα σε όλους.", packageName = "com.google.android.gm"))
        assertEquals(listOf(DictationItem(1, "com.google.android.gm", NOW, 4)), history.list(10).first())
    }

    @Test
    fun `the search reads the raw text too, and highlights the real words`() = runTest {
        history.record(newDictation("εεε στείλε μου το αρχείο", "Στείλε μου το αρχείο.", profile = "mail"))
        val hit = history.search("αρχειο").first().single()
        assertEquals(Snippet("Στείλε μου το αρχείο.", listOf(14..19)), hit.snippet)
        // «εεε» is only in what was said: the profile took it out.
        val raw = history.search("εεε").first().single()
        assertEquals(Snippet("εεε στείλε μου το αρχείο", listOf(0..2)), raw.snippet)
    }

    @Test
    fun `a search below two characters finds nothing`() = runTest {
        history.record(newDictation("α β γ"))
        assertEquals(emptyList<SearchResult>(), history.search("α").first())
    }

    @Test
    fun `the detail has everything recorded, with the profile asked for`() = runTest {
        history.record(newDictation("καλημέρα", "Καλημέρα.", profile = "chatter", audioS = 1.5))
        val d = history.get(history.list(1).first().single().id)!!
        assertEquals("chatter", d.profile)
        assertEquals(NOW - 3_000, d.startedAt)
        assertEquals(NOW, d.createdAt)
        assertEquals(1, d.words)
        assertEquals(40.0, d.wpm!!, 1e-9)
        assertEquals("user", d.endReason)
        assertEquals("COMMIT_TEXT", d.insertMethod)
    }

    @Test
    fun `a broken connection keeps the profile asked for, without WPM`() = runTest {
        history.record(newDictation("μέχρι εδώ", profile = "mail", audioS = null))
        val d = history.get(history.list(1).first().single().id)!!
        assertEquals("mail", d.profile)
        assertEquals("connection_lost", d.endReason)
        assertEquals(2, d.words)
        assertNull(d.wpm)
    }

    @Test
    fun `incognito leaves no row`() = runTest {
        val delivery = OutcomeDelivery(
            inserter = object : Inserter {
                override suspend fun insert(text: String) = InsertMethod.COMMIT_TEXT
            },
            history = history,
            log = FakeLog(),
            clock = { NOW },
        )
        val final = Final("γεια", "Γεια.", "raw", null, "user", Stats(1.0, 1, 150, null, 160))
        val chrome = FieldContext(packageName = "com.android.chrome", isIncognito = true)
        delivery.deliver(Outcome.Done(final), Start(fieldContext = chrome), NOW)
        assertEquals(0, history.count())
        assertEquals(0, stats.totals().first().dictations)

        delivery.deliver(Outcome.Done(final), Start(fieldContext = chrome.copy(isIncognito = false)), NOW)
        assertEquals(1, history.count())
    }
}
