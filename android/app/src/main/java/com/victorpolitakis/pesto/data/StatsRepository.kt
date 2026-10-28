package com.victorpolitakis.pesto.data

import com.victorpolitakis.pesto.util.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The stats of Home and Analytics (C10, Δ8): SQL over the dictations table
 * only, so they never drift from the History, and a deleted dictation leaves
 * them too. Incognito dictations are not in the table, so not here (Δ7).
 * «Today» starts at 00:00 and «this week» on Monday at 00:00, local time.
 * TODO(X8): a Glance widget of the stats reads them from here.
 */
@Singleton
class StatsRepository(
    private val dao: DictationDao,
    private val clock: WallClock,
    private val zone: () -> ZoneId,
) {
    @Inject constructor(dao: DictationDao, clock: WallClock) : this(dao, clock, ZoneId::systemDefault)

    /** The days are fixed when the flow is collected: a screen open over midnight keeps yesterday's. */
    fun totals(): Flow<Totals> {
        val today = today()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return dao.totals(startOf(today), startOf(monday)).map { it.toTotals() }
    }

    /** The last [days] days, today included, oldest first, with the days without dictations. */
    fun perDay(days: Int): Flow<List<DayCount>> {
        val first = today().minusDays(days - 1L)
        return dao.since(startOf(first)).map { rows ->
            val byDay = rows.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone()).toLocalDate() }
            (0 until days).map { i ->
                val day = first.plusDays(i.toLong())
                val dictations = byDay[day].orEmpty()
                DayCount(day, dictations.size, dictations.sumOf { it.words })
            }
        }
    }

    fun apps(): Flow<List<AppUsage>> = dao.apps()

    fun byProfile(): Flow<List<KeyCount>> = dao.byProfile()

    fun byFallbackReason(): Flow<List<KeyCount>> = dao.byFallbackReason()

    fun byEndReason(): Flow<List<KeyCount>> = dao.byEndReason()

    fun byInsertMethod(): Flow<List<KeyCount>> = dao.byInsertMethod()

    private fun today(): LocalDate = Instant.ofEpochMilli(clock.now()).atZone(zone()).toLocalDate()

    private fun startOf(day: LocalDate): Long = day.atStartOfDay(zone()).toInstant().toEpochMilli()

    private fun TotalsRow.toTotals() = Totals(
        dictations = dictations,
        dictationsToday = dictationsToday,
        words = words,
        wordsToday = wordsToday,
        wordsThisWeek = wordsThisWeek,
        speechMinutes = audioSeconds / 60,
        minutesSaved = minutesSaved,
        wpm = if (audioSeconds > 0) wordsWithAudio / (audioSeconds / 60) else null,
    )

    companion object {
        /** Δ8: the typing speed the minutes saved are measured against (the 40.0 of DictationDao.totals). */
        const val TYPING_WPM = 40
    }
}
