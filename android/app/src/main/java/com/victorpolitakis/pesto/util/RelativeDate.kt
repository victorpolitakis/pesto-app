package com.victorpolitakis.pesto.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * When a dictation ended, as the lists say it (Δ23): «Σήμερα, 18:26», «Χθες,
 * 18:26», the day of the week for the last 7 days («Τρίτη, 18:26»), else the
 * date («12 Οκτ, 18:26», with the year when it is not this one). 12 or 24 hours
 * as the phone is set. Pure: the words for today and yesterday, and the [locale]
 * of the names of the days and months, come in («Tuesday», «12 Oct» in English).
 */
object RelativeDate {
    fun format(time: Long, now: Long, zone: ZoneId, is24h: Boolean, today: String, yesterday: String, locale: Locale): String {
        val at = Instant.ofEpochMilli(time).atZone(zone)
        val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(at.toLocalDate(), nowDate)
        val day = when (days) {
            0L -> today
            1L -> yesterday
            in 2L..6L -> at.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            else -> at.format(DateTimeFormatter.ofPattern(if (at.year == nowDate.year) "d MMM" else "d MMM yyyy", locale))
        }
        return "$day, ${clock(time, zone, is24h, locale)}"
    }

    /** The exact time, for the History detail: «28 Οκτ 2026, 18:26:03». */
    fun exact(time: Long, zone: ZoneId, is24h: Boolean, locale: Locale): String {
        val at = Instant.ofEpochMilli(time).atZone(zone)
        return at.format(DateTimeFormatter.ofPattern(if (is24h) "d MMM yyyy, HH:mm:ss" else "d MMM yyyy, h:mm:ss a", locale))
    }

    /** A day of the bars of Analytics, for TalkBack: «Τρίτη 27 Οκτ». */
    fun day(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofPattern("EEEE d MMM", locale))

    /** The short name of its weekday, under a bar: «Τρί». */
    fun weekday(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    private fun clock(time: Long, zone: ZoneId, is24h: Boolean, locale: Locale): String =
        Instant.ofEpochMilli(time).atZone(zone).format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale))
}
