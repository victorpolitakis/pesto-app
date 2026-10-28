package com.victorpolitakis.pesto.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.Totals
import com.victorpolitakis.pesto.ui.assertAccessible
import com.victorpolitakis.pesto.ui.assertCenteredOnScreen
import com.victorpolitakis.pesto.ui.assertLayout
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** C10: Home in the order of the sketch, the four pages of Δ21, the buttons without logic, and where each way goes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private val whatsapp = AppDisplay("com.whatsapp", "WhatsApp", null)

    private val state = HomeState(
        totals = Totals(
            dictations = 12, dictationsToday = 2, words = 900, wordsToday = 80, wordsThisWeek = 400,
            speechMinutes = 6.0, minutesSaved = 16.5, wpm = 150.0,
        ),
        topApps = listOf(whatsapp),
        recent = List(5) { i -> DictationRowUi(i + 1L, whatsapp, System.currentTimeMillis(), 10) },
        loading = false,
    )

    private fun show(s: HomeState = state) {
        rule.setContent {
            PestoTheme {
                HomeScreen(
                    s,
                    onOpenSettings = { opened += "settings" },
                    onOpenSearch = { opened += "search" },
                    onOpenAnalytics = { opened += "analytics" },
                    onOpenApps = { opened += "apps" },
                    onOpenLanguages = { opened += "languages" },
                    onOpenHistory = { opened += "history" },
                    onOpenDictation = { opened += "dictation $it" },
                )
            }
        }
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `the order of the sketch, from the top down`() {
        show()
        val tops = listOf("home_top", "home_pager", "home_apps", "home_dots", "home_actions", "home_recent").map { bounds(it).top }
        tops.zipWithNext().forEach { (above, below) -> assertTrue("$above is not above $below", above < below) }
    }

    @Test
    fun `the top row - avatar, search, and two circles of the same size`() {
        show()
        val lefts = listOf("home_avatar", "home_search", "home_analytics", "home_chatbot").map { bounds(it).left }
        lefts.zipWithNext().forEach { (left, right) -> assertTrue(left < right) }
        val analytics = bounds("home_analytics")
        val chatbot = bounds("home_chatbot")
        assertEquals(analytics.right - analytics.left, chatbot.right - chatbot.left)
        assertEquals(analytics.bottom - analytics.top, chatbot.bottom - chatbot.top)
        assertEquals(analytics.right - analytics.left, analytics.bottom - analytics.top)
    }

    @Test
    fun `the big number has four pages, the minutes saved first`() {
        show()
        rule.onNodeWithTag("home_page_minutes_saved")
            .assertIsDisplayed()
            .assertTextContains("Λεπτά που κέρδισες")
            .assertTextContains("17")
            .assertTextContains("σε σχέση με 40 λέξεις/λεπτό πληκτρολόγηση")
        rule.onNodeWithTag("home_dots").assertContentDescriptionEquals("Σελίδα 1 από 4")

        val next = listOf(
            "words_this_week" to listOf("Λέξεις αυτή την εβδομάδα", "400", "σήμερα: 80"),
            "dictations" to listOf("Φορές χρήσης", "12", "σήμερα: 2"),
            "wpm" to listOf("Λέξεις το λεπτό (WPM)", "150"),
        )
        for ((i, page) in next.withIndex()) {
            rule.onNodeWithTag("home_pager").performTouchInput { swipeLeft() }
            val node = rule.onNodeWithTag("home_page_${page.first}").assertIsDisplayed()
            page.second.forEach { node.assertTextContains(it) }
            rule.onNodeWithTag("home_dots").assertContentDescriptionEquals("Σελίδα ${i + 2} από 4")
        }
    }

    @Test
    fun `the buttons without logic yet do nothing, and do not crash`() {
        show()
        rule.onNodeWithTag("home_chatbot").performClick()
        rule.onNodeWithTag("home_add_styles").performScrollTo().performClick()
        rule.onNodeWithTag("home_edit").performClick()
        rule.onNodeWithTag("home_more").performClick()
        rule.onNodeWithTag("home_more_item").assertIsDisplayed().performClick()
        rule.onNodeWithTag("home_more_item").assertDoesNotExist()
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `every way out of Home`() {
        show()
        rule.onNodeWithTag("home_avatar").performClick()
        rule.onNodeWithTag("home_search").performClick()
        rule.onNodeWithTag("home_analytics").performClick()
        rule.onNodeWithTag("home_apps").performScrollTo().performClick()
        rule.onNodeWithTag("home_languages").performScrollTo().performClick()
        rule.onNodeWithTag("home_all").performScrollTo().performClick()
        rule.onNodeWithTag("dictation_5").performScrollTo().performClick()
        assertEquals(listOf("settings", "search", "analytics", "apps", "languages", "history", "dictation 5"), opened)
    }

    @Test
    fun `an empty Home, before the first dictation`() {
        show(HomeState(loading = false))
        rule.onNodeWithTag("home_page_minutes_saved").assertIsDisplayed()
        rule.onNodeWithTag("home_apps").assertTextContains("Εφαρμογές")
        rule.onNodeWithTag("empty").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `levels 2 and 3 - layout and accessibility, with long Greek names and large numbers`() {
        val long = AppDisplay("gr.example", "Μια εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα που δεν χωράει σε μία γραμμή", null)
        show(
            state.copy(
                totals = state.totals.copy(minutesSaved = 54_321.0, wordsThisWeek = 1_234_567, dictations = 98_765),
                topApps = List(3) { long },
                recent = List(5) { i -> DictationRowUi(i + 1L, long, System.currentTimeMillis(), 12_345) },
            ),
        )
        rule.assertLayout()
        rule.assertCenteredOnScreen("home_value")
        rule.assertCenteredOnScreen("home_dots")
        rule.assertAccessible()
    }
}
