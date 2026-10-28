package com.victorpolitakis.pesto.ui.analytics

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.DayCount
import com.victorpolitakis.pesto.data.KeyCount
import com.victorpolitakis.pesto.data.Totals
import com.victorpolitakis.pesto.ui.assertAccessible
import com.victorpolitakis.pesto.ui.assertLayout
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** C10: the totals, the bars of 7 and 30 days, and «Αξιοπιστία» for phase 4. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalyticsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // Wednesday 28/10/2026 last, with 120 words in 3 dictations.
    private val days = List(AnalyticsState.MONTH) { i ->
        val last = i == AnalyticsState.MONTH - 1
        DayCount(LocalDate.of(2026, 10, 28).minusDays((AnalyticsState.MONTH - 1 - i).toLong()), if (last) 3 else 1, if (last) 120 else 10)
    }

    private val state = AnalyticsState(
        totals = Totals(dictations = 12, words = 900, speechMinutes = 6.0, minutesSaved = 16.5, wpm = 150.0),
        days = days,
        apps = listOf(AppCount(AppDisplay("com.whatsapp", "WhatsApp", null), 12)),
        profiles = listOf(KeyCount("mail", 12)),
        fallbackReasons = listOf(KeyCount(null, 10), KeyCount("timeout", 2)),
        endReasons = listOf(KeyCount("user", 11), KeyCount("connection_lost", 1)),
        insertMethods = listOf(KeyCount("COMMIT_TEXT", 12)),
        loading = false,
    )

    private fun show(s: AnalyticsState = state, period: Int = AnalyticsState.WEEK, onDaysChange: (Int) -> Unit = {}) {
        rule.setContent { PestoTheme { AnalyticsScreen(s, period, onDaysChange = onDaysChange, onBack = {}) } }
    }

    @Test
    fun `the totals, and what the minutes saved are counted against`() {
        show()
        rule.onNodeWithTag("total_words").assertTextContains("900")
        rule.onNodeWithTag("total_dictations").assertTextContains("12")
        rule.onNodeWithTag("total_saved").assertTextContains("σε σχέση με 40 λέξεις/λεπτό πληκτρολόγηση")
        rule.onNodeWithTag("total_wpm").assertTextContains("150")
    }

    @Test
    fun `the bars of the week, then of the month`() {
        var chosen = 0
        show(onDaysChange = { chosen = it })
        rule.onNodeWithTag("analytics").performScrollToNode(hasTestTag("day_bars"))
        rule.onAllNodesWithTag("day_bar").assertCountEquals(7)
        rule.onAllNodesWithTag("day_bar")[6].assertContentDescriptionEquals("Τετάρτη 28 Οκτ: 120 λέξεις, 3 υπαγορεύσεις")
        rule.onNodeWithTag("analytics").performScrollToNode(hasTestTag("days_30"))
        rule.onNodeWithTag("days_30").performClick()
        assertEquals(30, chosen)
    }

    @Test
    fun `the month has 30 bars`() {
        show(period = AnalyticsState.MONTH)
        rule.onNodeWithTag("analytics").performScrollToNode(hasTestTag("day_bars"))
        rule.onAllNodesWithTag("day_bar").assertCountEquals(30)
    }

    @Test
    fun `per app, per profile, and where something fails`() {
        show()
        val list = rule.onNodeWithTag("analytics")
        list.performScrollToNode(hasTestTag("analytics_app_com.whatsapp"))
        rule.onNodeWithTag("analytics_app_com.whatsapp").assertTextContains("WhatsApp")
        list.performScrollToNode(hasTestTag("count_profiles_mail"))
        rule.onNodeWithTag("count_profiles_mail").assertTextContains("Email (mail)")
        list.performScrollToNode(hasTestTag("count_fallback_null"))
        rule.onNodeWithTag("count_fallback_null").assertTextContains("Κανένας: το προφίλ εφαρμόστηκε").assertTextContains("10")
        list.performScrollToNode(hasTestTag("count_end_connection_lost"))
        rule.onNodeWithTag("count_end_connection_lost").assertTextContains("connection_lost")
        list.performScrollToNode(hasTestTag("count_insert_COMMIT_TEXT"))
        rule.onNodeWithTag("count_insert_COMMIT_TEXT").assertIsDisplayed()
    }

    @Test
    fun `no numbers before the first dictation`() {
        show(AnalyticsState(loading = false))
        rule.onNodeWithTag("empty").assertIsDisplayed()
    }

    @Test
    fun `levels 2 and 3 - layout and accessibility, the month, with long Greek names`() {
        val long = AppDisplay("gr.example", "Μια εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα που δεν χωράει σε μία γραμμή", null)
        show(
            state.copy(
                totals = state.totals.copy(words = 1_234_567, dictations = 98_765),
                apps = List(3) { i -> AppCount(long.copy(packageName = "gr.example.app$i"), 12_345) },
            ),
            period = AnalyticsState.MONTH,
        )
        rule.assertLayout()
        rule.assertAccessible()
    }
}
