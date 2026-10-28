package com.victorpolitakis.pesto.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The numbers of Home and Analytics (Δ8, Δ21) from known dictations. */
@RunWith(AndroidJUnit4::class)
class StatsRepositoryTest {
    private val db = inMemoryDatabase()
    private val history = HistoryRepository(db.dictations())
    private val stats = StatsRepository(db.dictations(), clock = { NOW }, zone = { ZoneOffset.UTC })

    @After fun close() = db.close()

    /**
     * Today: 40 words in 30 s (saves 1 − 0.5 = 0.5 min) and a broken connection of 20 words.
     * Yesterday: 80 words in 60 s (saves 2 − 1 = 1). Ten days ago: 10 words in 6 s (0.25 − 0.1).
     */
    private suspend fun known() {
        history.record(newDictation(words(40), packageName = "com.whatsapp", profile = "chatter", createdAt = NOW, audioS = 30.0))
        history.record(
            newDictation(
                words(80), packageName = "com.google.android.gm", profile = "mail", createdAt = NOW - DAY,
                audioS = 60.0, fallbackReason = "guard:input",
            ),
        )
        history.record(newDictation(words(20), packageName = "com.whatsapp", profile = "chatter", createdAt = NOW - 60_000, audioS = null))
        history.record(
            newDictation(
                words(10), packageName = "com.google.android.gm", profile = "raw", createdAt = NOW - 10 * DAY,
                audioS = 6.0, insertMethod = "CLIPBOARD",
            ),
        )
    }

    @Test
    fun `totals of known dictations`() = runTest {
        known()
        val t = stats.totals().first()
        assertEquals(4, t.dictations)
        assertEquals(2, t.dictationsToday)
        assertEquals(150, t.words)
        assertEquals(60, t.wordsToday)
        assertEquals(140, t.wordsThisWeek)
        assertEquals(1.6, t.speechMinutes, 1e-9)
        assertEquals(1.65, t.minutesSaved, 1e-9)
        // The broken connection has no audio: not in WPM.
        assertEquals(130 / 1.6, t.wpm!!, 1e-9)
    }

    @Test
    fun `a slow dictation saves nothing, it never subtracts`() = runTest {
        history.record(newDictation(words(2), audioS = 30.0))
        assertEquals(0.0, stats.totals().first().minutesSaved, 1e-9)
    }

    @Test
    fun `without audio there is no WPM, but the words and the uses count`() = runTest {
        assertNull(stats.totals().first().wpm)
        history.record(newDictation(words(5), audioS = null))
        val t = stats.totals().first()
        assertNull(t.wpm)
        assertEquals(5, t.words)
        assertEquals(1, t.dictations)
        assertEquals(0.0, t.minutesSaved, 1e-9)
    }

    @Test
    fun `the week starts on Monday at 00 00`() = runTest {
        val monday = LocalDateTime.of(2026, 10, 26, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        history.record(newDictation(words(3), createdAt = monday))
        history.record(newDictation(words(7), createdAt = monday - 1))
        val t = stats.totals().first()
        assertEquals(3, t.wordsThisWeek)
        assertEquals(10, t.words)
        assertEquals(0, t.dictationsToday)
    }

    @Test
    fun `per day, the last 7 and 30 days with the empty ones`() = runTest {
        known()
        val days = stats.perDay(7).first()
        assertEquals((22..28).map { LocalDate.of(2026, 10, it) }, days.map { it.day })
        assertEquals(DayCount(LocalDate.of(2026, 10, 27), 1, 80), days[5])
        assertEquals(DayCount(LocalDate.of(2026, 10, 28), 2, 60), days[6])
        assertEquals(0, days.take(5).sumOf { it.dictations })
        val month = stats.perDay(30).first()
        assertEquals(30, month.size)
        assertEquals(4, month.sumOf { it.dictations })
    }

    @Test
    fun `apps, profiles and reliability`() = runTest {
        known()
        assertEquals(listOf(AppUsage("com.whatsapp", 2, NOW), AppUsage("com.google.android.gm", 2, NOW - DAY)), stats.apps().first())
        assertEquals(KeyCount("chatter", 2), stats.byProfile().first().first())
        assertEquals(setOf(KeyCount("chatter", 2), KeyCount("mail", 1), KeyCount("raw", 1)), stats.byProfile().first().toSet())
        assertEquals(setOf(KeyCount(null, 3), KeyCount("guard:input", 1)), stats.byFallbackReason().first().toSet())
        assertEquals(setOf(KeyCount("user", 3), KeyCount("connection_lost", 1)), stats.byEndReason().first().toSet())
        assertEquals(setOf(KeyCount("COMMIT_TEXT", 3), KeyCount("CLIPBOARD", 1)), stats.byInsertMethod().first().toSet())
    }

    @Test
    fun `deleting a dictation changes the stats`() = runTest {
        known()
        history.delete(history.list(1).first().single().id)
        val t = stats.totals().first()
        assertEquals(3, t.dictations)
        assertEquals(110, t.words)
    }
}
