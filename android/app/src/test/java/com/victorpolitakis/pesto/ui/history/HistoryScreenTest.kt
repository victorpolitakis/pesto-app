package com.victorpolitakis.pesto.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** C8: the rows of the History and what they open. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun rows(count: Int, name: String = "Gmail") = List(count) { i ->
        DictationRowUi(i.toLong() + 1, AppDisplay("com.google.android.gm", name, null), System.currentTimeMillis(), 56)
    }

    @Test
    fun `an empty History says so`() {
        rule.setContent { PestoTheme { HistoryScreen(HistoryState(loading = false), onOpenDictation = {}, onLoadMore = {}) } }
        rule.onNodeWithTag("empty").assertIsDisplayed()
    }

    @Test
    fun `a row has the app, the time and the words, and opens the dictation`() {
        var opened: Long? = null
        rule.setContent {
            PestoTheme { HistoryScreen(HistoryState(rows(3), total = 3, loading = false), onOpenDictation = { opened = it }, onLoadMore = {}) }
        }
        rule.onNodeWithTag("history_count").assertIsDisplayed()
        rule.onNodeWithText("3 υπαγορεύσεις").assertIsDisplayed()
        // The row is one node for TalkBack (clickable): the app, the time and the words in it.
        rule.onNodeWithTag("dictation_2")
            .assertIsDisplayed()
            .assertTextContains("Gmail")
            .assertTextContains("Σήμερα", substring = true)
            .assertTextContains("56 λέξεις")
        rule.onNodeWithTag("dictation_2").performClick()
        assertEquals(2L, opened)
    }

    @Test
    fun `the end of the list loads the next page`() {
        var loads = 0
        rule.setContent {
            PestoTheme { HistoryScreen(HistoryState(rows(30), total = 250, loading = false), onOpenDictation = {}, onLoadMore = { loads++ }) }
        }
        assertEquals(0, loads)
        // Index 30: the item after the 30 rows, the end of the list.
        rule.onNodeWithTag("history_list").performScrollToIndex(30)
        rule.waitForIdle()
        assertEquals(1, loads)
    }

    @Test
    fun `a whole page loads nothing more`() {
        var loads = 0
        rule.setContent {
            PestoTheme { HistoryScreen(HistoryState(rows(5), total = 5, loading = false), onOpenDictation = {}, onLoadMore = { loads++ }) }
        }
        rule.waitForIdle()
        assertEquals(0, loads)
    }

    @Test
    fun `a very long Greek app name stays in its row`() {
        rule.setContent {
            PestoTheme {
                HistoryScreen(
                    HistoryState(rows(2, "Μια εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα που δεν χωράει σε μία γραμμή"), total = 2, loading = false),
                    onOpenDictation = {},
                    onLoadMore = {},
                )
            }
        }
        rule.onNodeWithTag("dictation_1").assertIsDisplayed()
    }
}
