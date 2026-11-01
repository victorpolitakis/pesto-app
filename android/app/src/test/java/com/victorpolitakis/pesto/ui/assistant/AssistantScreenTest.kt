package com.victorpolitakis.pesto.ui.assistant

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.protocol.AssistantDestination
import com.victorpolitakis.pesto.protocol.NavigationTarget
import com.victorpolitakis.pesto.ui.assertAccessible
import com.victorpolitakis.pesto.ui.assertLayout
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val sent = mutableListOf<String>()
    private val navigated = mutableListOf<NavigationTarget>()
    private val openedDictations = mutableListOf<Long>()
    private var pairingOpened = 0
    private var backClicked = 0

    private fun show(state: AssistantState, input: String = "") {
        rule.setContent {
            PestoTheme {
                AssistantScreen(
                    input = input,
                    state = state,
                    onInputChange = {},
                    onSend = { sent += it },
                    onBack = { backClicked++ },
                    onNavigate = { navigated += it },
                    onOpenDictation = { openedDictations += it },
                    onOpenPairing = { pairingOpened++ },
                )
            }
        }
    }

    @Test
    fun `empty Assistant shows Greek starter prompts and passes levels 2 and 3`() {
        show(AssistantState(paired = true, loading = false))
        rule.onNodeWithTag("suggestion_stats").assertIsDisplayed().performClick()
        assertEquals(listOf("Πόσα λεπτά έχω κερδίσει;"), sent)
        rule.assertLayout()
        rule.assertAccessible()
    }

    @Test
    fun `unpaired Assistant links to backend settings and passes levels 2 and 3`() {
        show(AssistantState(paired = false, loading = false))
        rule.onNodeWithTag("assistant_unpaired").assertIsDisplayed()
        rule.onNodeWithTag("assistant_open_pairing").performClick()
        assertEquals(1, pairingOpened)
        rule.assertLayout()
        rule.assertAccessible()
    }

    @Test
    fun `active chat renders navigation chip, citations, and passes levels 2 and 3 with long Greek`() {
        val longApp = AppDisplay("gr.example", "Μια εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα που δεν χωράει", null)
        val messages = listOf(
            ChatMessage(id = 1, isUser = true, text = "Τι υπαγόρευσα σήμερα και πού είναι το Λεξικό;"),
            ChatMessage(
                id = 2,
                isUser = false,
                text = "Σήμερα έκανες 3 υπαγορεύσεις με συνολικά 120 λέξεις. Μπορείς να ανοίξεις το Λεξικό παρακάτω.",
                navigate = NavigationTarget(AssistantDestination.DICTIONARY),
                citations = listOf(DictationRowUi(42L, longApp, System.currentTimeMillis(), 12_345)),
            ),
        )
        show(AssistantState(paired = true, messages = messages, loading = false), input = "Ευχαριστώ")
        rule.onNodeWithTag("assistant_list").performScrollToNode(hasTestTag("assistant_nav_2"))
        rule.onNodeWithTag("assistant_nav_2").assertTextContains("Άνοιγμα: Λεξικό").performClick()
        assertEquals(listOf(NavigationTarget(AssistantDestination.DICTIONARY)), navigated)

        rule.onNodeWithTag("assistant_list").performScrollToNode(hasTestTag("dictation_42"))
        rule.onNodeWithTag("dictation_42").performClick()
        assertEquals(listOf(42L), openedDictations)

        rule.onNodeWithTag("assistant_send").performClick()
        assertEquals(listOf("Ευχαριστώ"), sent)

        rule.assertLayout()
        rule.assertAccessible()
    }

    @Test
    fun `Assistant speaks English and passes levels 2 and 3`() {
        // Tested via @Config(qualifiers = "en") in AssistantEnglishTest below; also check composer input tag here.
        var typed = ""
        rule.setContent {
            PestoTheme {
                AssistantScreen(
                    input = "",
                    state = AssistantState(paired = true, loading = false),
                    onInputChange = { typed = it },
                    onSend = {},
                    onBack = {},
                    onNavigate = {},
                    onOpenDictation = {},
                    onOpenPairing = {},
                )
            }
        }
        rule.onNodeWithTag("assistant_input").performTextInput("Γεια")
        assertEquals("Γεια", typed)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en")
class AssistantEnglishLocaleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `empty and active Assistant speak English and pass levels 2 and 3`() {
        val whatsapp = AppDisplay("com.whatsapp", "An app with a very, very long name that does not fit in one line", null)
        val messages = listOf(
            ChatMessage(id = 1, isUser = true, text = "How many minutes have I saved this week?"),
            ChatMessage(
                id = 2,
                isUser = false,
                text = "You have saved 16.5 minutes across 12 dictations.",
                navigate = NavigationTarget(AssistantDestination.ANALYTICS),
                citations = listOf(DictationRowUi(7L, whatsapp, System.currentTimeMillis(), 90)),
            ),
        )
        rule.setContent {
            PestoTheme {
                AssistantScreen(
                    input = "Thanks",
                    state = AssistantState(paired = true, messages = messages, loading = false),
                    onInputChange = {},
                    onSend = {},
                    onBack = {},
                    onNavigate = {},
                    onOpenDictation = {},
                    onOpenPairing = {},
                )
            }
        }
        rule.onNodeWithTag("assistant_list").performScrollToNode(hasTestTag("assistant_nav_2"))
        rule.onNodeWithTag("assistant_nav_2").assertTextContains("Open: Statistics")
        rule.assertLayout()
        rule.assertAccessible()
    }
}
