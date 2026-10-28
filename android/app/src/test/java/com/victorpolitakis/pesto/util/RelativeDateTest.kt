package com.victorpolitakis.pesto.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Δ23, on Wednesday 28/10/2026 at 20:00 (UTC unless said otherwise). */
class RelativeDateTest {
    private val now = at(2026, 10, 28, 20, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun format(time: Long, is24h: Boolean = true, zone: ZoneId = ZoneOffset.UTC) =
        RelativeDate.format(time, now, zone, is24h, today = "Σήμερα", yesterday = "Χθες")

    @Test
    fun `today and yesterday`() {
        assertEquals("Σήμερα, 18:26", format(at(2026, 10, 28, 18, 26)))
        assertEquals("Χθες, 18:26", format(at(2026, 10, 27, 18, 26)))
    }

    @Test
    fun `yesterday is the calendar day, not 24 hours`() {
        val justAfterMidnight = at(2026, 10, 28, 0, 1)
        assertEquals("Χθες, 23:59", RelativeDate.format(at(2026, 10, 27, 23, 59), justAfterMidnight, ZoneOffset.UTC, true, "Σήμερα", "Χθες"))
    }

    @Test
    fun `the day of the week for the last 7 days`() {
        assertEquals("Δευτέρα, 09:05", format(at(2026, 10, 26, 9, 5)))
        assertEquals("Πέμπτη, 18:26", format(at(2026, 10, 22, 18, 26)))
    }

    @Test
    fun `older, the date, with the year when it is another one`() {
        assertEquals("21 Οκτ, 18:26", format(at(2026, 10, 21, 18, 26)))
        assertEquals("12 Οκτ 2025, 18:26", format(at(2025, 10, 12, 18, 26)))
    }

    @Test
    fun `12 hours as the phone is set`() {
        assertTrue(format(at(2026, 10, 27, 18, 26), is24h = false).startsWith("Χθες, 6:26"))
        assertTrue(format(at(2026, 10, 28, 9, 5), is24h = false).startsWith("Σήμερα, 9:05"))
    }

    @Test
    fun `the day is the local one`() {
        // 23:30 UTC on Tuesday is 01:30 on Wednesday in Athens (UTC+2 after the end of summer time).
        assertEquals("Σήμερα, 01:30", format(at(2026, 10, 27, 23, 30), zone = ZoneId.of("Europe/Athens")))
    }

    @Test
    fun `the exact time of the detail`() {
        assertEquals("28 Οκτ 2026, 18:26:03", RelativeDate.exact(at(2026, 10, 28, 18, 26) + 3_000, ZoneOffset.UTC, true))
    }
}
