package com.victorpolitakis.pesto.ui.apps

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.ui.assertAccessible
import com.victorpolitakis.pesto.ui.assertLayout
import com.victorpolitakis.pesto.ui.assertLeftAligned
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** C10 and C9: the apps of the History, and the profile of each. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val rows = listOf(
        AppRow(AppDisplay("com.whatsapp", "WhatsApp", null), 54, System.currentTimeMillis(), "chatter"),
        AppRow(AppDisplay("com.google.android.gm", "Gmail", null), 1, System.currentTimeMillis(), null),
    )

    private fun show(state: AppsState, onChoose: (String) -> Unit = {}, onSetProfile: (String?) -> Unit = {}) {
        rule.setContent {
            PestoTheme { AppsScreen(state, onBack = {}, onChoose = onChoose, onSetProfile = onSetProfile, onDismiss = {}) }
        }
    }

    @Test
    fun `a row has the app, its dictations, its last use and its profile`() {
        show(AppsState(rows, defaultProfile = "mail", loading = false))
        rule.onNodeWithTag("app_com.whatsapp")
            .assertTextContains("WhatsApp")
            .assertTextContains("54 υπαγορεύσεις · τελευταία Σήμερα", substring = true)
            .assertTextContains("Συνομιλία")
        // Without a profile of its own: automatic, and which one that is today.
        rule.onNodeWithTag("app_com.google.android.gm")
            .assertTextContains("1 υπαγόρευση · τελευταία Σήμερα", substring = true)
            .assertTextContains("Αυτόματο (Email)")
    }

    @Test
    fun `a row opens the choice of its profile`() {
        var chosen: String? = null
        show(AppsState(rows, defaultProfile = "mail", loading = false), onChoose = { chosen = it })
        rule.onNodeWithTag("app_com.whatsapp").performClick()
        assertEquals("com.whatsapp", chosen)
    }

    @Test
    fun `the dialog sets one profile, or automatic`() {
        var set: String? = "nothing yet"
        show(AppsState(rows, defaultProfile = "mail", choosing = "com.whatsapp", loading = false), onSetProfile = { set = it })
        rule.onNodeWithTag("profile_dialog").assertIsDisplayed()
        rule.onNodeWithTag("choice_chatter").assertIsSelected()
        rule.onNodeWithTag("choice_automatic").assertIsNotSelected()

        // The choices scroll inside the dialog on a small screen.
        rule.onNodeWithTag("choice_professional").performScrollTo().performClick()
        assertEquals("professional", set)
        rule.onNodeWithTag("choice_automatic").performScrollTo().assertTextContains("Αυτόματο: η προεπιλογή (Email)").performClick()
        assertNull(set)
    }

    @Test
    fun `no app before the first dictation`() {
        show(AppsState(loading = false))
        rule.onNodeWithTag("empty").assertIsDisplayed()
    }

    @Test
    fun `levels 2 and 3 - layout and accessibility, with long Greek names`() {
        val long = List(6) { i -> AppRow(AppDisplay("gr.example.app$i", "Μια εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα που δεν χωράει σε μία γραμμή $i", null), 12_345, 0, if (i % 2 == 0) null else "professional") }
        show(AppsState(long, defaultProfile = "professional", loading = false))
        rule.assertLayout()
        rule.assertLeftAligned("app_gr.example.app0", "app_gr.example.app1")
        rule.assertAccessible()
    }
}
