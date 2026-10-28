package com.victorpolitakis.pesto.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.Snippet
import com.victorpolitakis.pesto.ui.assertAccessible
import com.victorpolitakis.pesto.ui.assertLayout
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** C8: the field, the hint below two letters, the results and what they open. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val hit = SearchHit(
        DictationRowUi(4, AppDisplay("com.whatsapp", "WhatsApp", null), System.currentTimeMillis(), 9),
        Snippet("Καλημέρα Γιώργο, το meeting πάει", listOf(0..7)),
    )

    @Test
    fun `the field has the focus, and typing goes to the search`() {
        var query by mutableStateOf("")
        rule.setContent {
            PestoTheme { SearchScreen(query, SearchState(), onQueryChange = { query = it }, onBack = {}, onOpenDictation = {}) }
        }
        rule.onNodeWithTag("search_field").assertIsFocused()
        rule.onNodeWithTag("search_field").performTextInput("καλημ")
        assertEquals("καλημ", query)
    }

    @Test
    fun `below two letters it says so`() {
        rule.setContent { PestoTheme { SearchScreen("κ", SearchState(), onQueryChange = {}, onBack = {}, onOpenDictation = {}) } }
        rule.onNodeWithText("Γράψε τουλάχιστον 2 γράμματα").assertIsDisplayed()
    }

    @Test
    fun `the results with their count and snippet, and a result opens the dictation`() {
        var opened: Long? = null
        rule.setContent {
            PestoTheme { SearchScreen("καλημ", SearchState("καλημ", listOf(hit)), onQueryChange = {}, onBack = {}, onOpenDictation = { opened = it }) }
        }
        rule.onNodeWithText("1 αποτέλεσμα").assertIsDisplayed()
        rule.onNodeWithTag("snippet", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("dictation_4").performClick()
        assertEquals(4L, opened)
    }

    @Test
    fun `nothing found says so`() {
        rule.setContent { PestoTheme { SearchScreen("αύριο", SearchState("αύριο"), onQueryChange = {}, onBack = {}, onOpenDictation = {}) } }
        rule.onNodeWithTag("empty").assertIsDisplayed()
    }

    @Test
    fun `older results wait for the new query`() {
        rule.setContent {
            PestoTheme { SearchScreen("καλημέρα", SearchState("καλημ", listOf(hit)), onQueryChange = {}, onBack = {}, onOpenDictation = {}) }
        }
        rule.onNodeWithTag("search_results").assertDoesNotExist()
    }

    @Test
    fun `levels 2 and 3 - layout and accessibility, with long Greek results`() {
        val long = SearchHit(
            DictationRowUi(5, AppDisplay("gr.example", "Μια εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα που δεν χωράει σε μία γραμμή", null), System.currentTimeMillis(), 12_345),
            Snippet("Καλημέρα σε όλους, ".repeat(12), listOf(0..7)),
        )
        rule.setContent {
            PestoTheme { SearchScreen("καλημ", SearchState("καλημ", listOf(hit, long)), onQueryChange = {}, onBack = {}, onOpenDictation = {}) }
        }
        rule.assertLayout()
        rule.assertAccessible()
    }
}
