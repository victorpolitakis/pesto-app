package com.victorpolitakis.pesto.ui.dictionary

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.FakeTermDao
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.NOW
import com.victorpolitakis.pesto.protocol.MAX_TERMS_CHARS
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** C2: the screen with its ViewModel and the repository, on an in-memory table. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DictionaryScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val dao = FakeTermDao()
    private val dictionary = DictionaryRepository(dao, clock = { NOW })
    private var undo: (suspend () -> Unit)? = null

    private fun show() {
        val viewModel = DictionaryViewModel(dictionary)
        rule.setContent { PestoTheme { DictionaryRoute(offerUndo = { undo = it }, viewModel = viewModel) } }
    }

    private fun add(term: String) {
        rule.onNodeWithTag("add_term").performClick()
        rule.onNodeWithTag("term_field").performTextInput(term)
        rule.onNodeWithTag("term_save").performClick()
    }

    private fun saved() = dao.rows.value.map { it.term }

    @Test
    fun `an empty Dictionary says so`() {
        show()
        rule.onNodeWithTag("empty").assertIsDisplayed()
        rule.onNodeWithTag("term_count").assertTextContains("0 όροι")
        rule.onNodeWithTag("add_term").assertIsEnabled()
    }

    @Test
    fun `a term is added, and the list stays in order`() {
        show()
        add("Πολιτάκης")
        rule.onNodeWithTag("term_dialog").assertDoesNotExist()
        add("Pesto")
        assertEquals(listOf("Πολιτάκης", "Pesto"), saved())
        rule.onNodeWithTag("term_count").assertTextContains("2 όροι")
        val rows = rule.onNodeWithTag("term_list").onChildren()
        rows[0].assertTextContains("Pesto")
        rows[1].assertTextContains("Πολιτάκης")
    }

    @Test
    fun `a duplicate stays in the dialog, and says why`() {
        show()
        add("Πολιτάκης")
        add("πολιτακης")
        rule.onNodeWithTag("term_dialog").assertIsDisplayed()
        rule.onNodeWithTag("term_message", useUnmergedTree = true).assertTextContains("Ο όρος υπάρχει ήδη")
        assertEquals(listOf("Πολιτάκης"), saved())

        rule.onNodeWithTag("term_cancel").performClick()
        rule.onNodeWithTag("term_dialog").assertDoesNotExist()
    }

    @Test
    fun `a term is edited from its row`() {
        runBlocking { dictionary.add("Πολιτακης") }
        show()
        rule.onNodeWithTag("term_1").performClick()
        rule.onNodeWithTag("term_field").assertTextContains("Πολιτακης")
        rule.onNodeWithTag("term_field").performTextReplacement("Πολιτάκης")
        rule.onNodeWithTag("term_save").performClick()
        rule.onNodeWithTag("term_dialog").assertDoesNotExist()
        assertEquals(listOf("Πολιτάκης"), saved())
        rule.onNodeWithTag("term_1").assertTextContains("Πολιτάκης")
    }

    @Test
    fun `a term is deleted, and the undo of the snackbar brings it back`() {
        runBlocking { dictionary.add("Πολιτάκης") }
        show()
        rule.onNodeWithTag("delete_term_1").performClick()
        rule.onNodeWithTag("empty").assertIsDisplayed()
        assertEquals(emptyList<String>(), saved())

        val restore = checkNotNull(undo) { "no undo offered" }
        runBlocking { restore() }
        rule.onNodeWithText("Πολιτάκης").assertIsDisplayed()
        assertEquals(listOf("Πολιτάκης"), saved())
    }

    @Test
    fun `the dialog counts what is left, and does not save past the limit`() {
        // 9 characters left: a term of 8 letters fits (8 and a separator), one of 9 does not.
        runBlocking { dictionary.add("α".repeat(MAX_TERMS_CHARS - 10)) }
        show()
        rule.onNodeWithTag("term_remaining").assertTextContains("Απομένουν 9 ", substring = true)
        rule.onNodeWithTag("add_term").performClick()

        rule.onNodeWithTag("term_field").performTextInput("Πολιτάκης")
        rule.onNodeWithTag("term_message", useUnmergedTree = true).assertTextContains("Δεν χωράει: 1 χαρακτήρας πάνω από το όριο")
        rule.onNodeWithTag("term_save").assertIsNotEnabled()

        rule.onNodeWithTag("term_field").performTextReplacement("Πολιτάκη")
        rule.onNodeWithTag("term_message", useUnmergedTree = true).assertTextContains("Απομένουν 0 χαρακτήρες")
        rule.onNodeWithTag("term_save").assertIsEnabled().performClick()
        assertEquals(2, saved().size)
    }

    @Test
    fun `a full Dictionary adds nothing more`() {
        runBlocking { dictionary.add("α".repeat(MAX_TERMS_CHARS - 2)) }
        show()
        rule.onNodeWithTag("add_term").assertIsNotEnabled()
        rule.onNodeWithTag("dictionary_full").assertIsDisplayed()
    }
}
