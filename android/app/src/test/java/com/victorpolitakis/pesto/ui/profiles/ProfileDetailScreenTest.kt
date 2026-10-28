package com.victorpolitakis.pesto.ui.profiles

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.ui.assertAccessible
import com.victorpolitakis.pesto.ui.assertLayout
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** C9: one profile, its default button, its apps and dictations. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileDetailScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val chatter = ProfilesCatalog.find("chatter")!!
    private val apps = listOf(
        ProfileAppUi(AppDisplay("com.whatsapp", "WhatsApp", null), automatic = false),
        ProfileAppUi(AppDisplay("org.telegram", "Telegram", null), automatic = true),
    )

    private fun show(state: ProfileDetailState, onSetDefault: () -> Unit = {}) {
        rule.setContent { PestoTheme { ProfileDetailScreen(state, onBack = {}, onSetDefault = onSetDefault) } }
    }

    @Test
    fun `the name, the sentence and the dictations of the profile`() {
        show(ProfileDetailState(chatter, dictations = 3, loading = false))
        rule.onNodeWithText("Συνομιλία").assertIsDisplayed()
        rule.onNodeWithTag("profile_description").assertTextContains("Φυσικό, φιλικό και σύντομο μήνυμα, όπως σε φίλο.")
        rule.onNodeWithTag("profile_dictations").assertTextContains("3 υπαγορεύσεις με αυτό το προφίλ")
        rule.onNodeWithTag("no_apps").assertIsDisplayed()
    }

    @Test
    fun `it becomes the default from its button`() {
        var set = 0
        show(ProfileDetailState(chatter, isDefault = false, loading = false), onSetDefault = { set++ })
        rule.onNodeWithTag("set_default").performClick()
        assertEquals(1, set)
    }

    @Test
    fun `the default has no button, it says so`() {
        show(ProfileDetailState(chatter, isDefault = true, loading = false))
        rule.onNodeWithTag("is_default").assertIsDisplayed()
        rule.onNodeWithTag("set_default").assertDoesNotExist()
    }

    @Test
    fun `the apps that get it, and which of them by the default`() {
        show(ProfileDetailState(chatter, isDefault = true, apps = apps, loading = false))
        rule.onNodeWithTag("no_apps").assertDoesNotExist()
        rule.onNodeWithTag("profile_app_com.whatsapp").assertTextContains("WhatsApp")
        rule.onNodeWithTag("profile_app_org.telegram")
            .assertTextContains("Telegram")
            .assertTextContains("Αυτόματα, ως προεπιλογή")
    }

    @Test
    fun `an unknown profile says so`() {
        show(ProfileDetailState(profile = null, loading = false))
        rule.onNodeWithTag("empty").assertIsDisplayed()
        rule.onNodeWithTag("set_default").assertDoesNotExist()
    }

    @Test
    fun `levels 2 and 3 - layout and accessibility, with long Greek app names`() {
        val many = List(6) { i -> ProfileAppUi(AppDisplay("gr.example.app$i", "Μια εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα που δεν χωράει σε μία γραμμή $i", null), i % 2 == 0) }
        show(ProfileDetailState(chatter, isDefault = false, apps = many, dictations = 56789, loading = false))
        rule.assertLayout()
        rule.assertAccessible()
    }
}
