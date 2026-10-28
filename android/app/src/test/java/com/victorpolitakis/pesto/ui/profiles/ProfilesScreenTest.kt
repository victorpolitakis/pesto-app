package com.victorpolitakis.pesto.ui.profiles

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.ui.assertAccessible
import com.victorpolitakis.pesto.ui.assertLayout
import com.victorpolitakis.pesto.ui.assertLeftAligned
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** C9: the ready-made profiles, the default, and what each row opens. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfilesScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val rows = ProfilesCatalog.all.map { p ->
        if (p.id == "mail") ProfileRow(p, isDefault = true, apps = 2, dictations = 14) else ProfileRow(p, false, apps = 0, dictations = 1)
    }

    private fun show(onOpen: (String) -> Unit = {}) {
        rule.setContent { PestoTheme { ProfilesScreen(ProfilesState(rows, loading = false), onOpenProfile = onOpen) } }
    }

    @Test
    fun `every ready-made profile is there, with its name and sentence`() {
        show()
        rule.onNodeWithTag("profile_raw")
            .assertTextContains("Χωρίς αλλαγές")
            .assertTextContains("Το κείμενο όπως το έγραψε η αναγνώριση φωνής, χωρίς επεξεργασία.")
        for (id in ProfilesCatalog.all.map { it.id }) {
            rule.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("profile_$id"))
            rule.onNodeWithTag("profile_$id").assertIsDisplayed()
        }
    }

    @Test
    fun `the default says so, with its apps and dictations`() {
        show()
        rule.onNodeWithTag("profile_raw")
            .assertTextContains("0 εφαρμογές · 1 υπαγόρευση")
            .assert(!hasText("Προεπιλογή"))
        rule.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("profile_mail"))
        rule.onNodeWithTag("profile_mail")
            .assertTextContains("Email")
            .assertTextContains("Προεπιλογή")
            .assertTextContains("2 εφαρμογές · 14 υπαγορεύσεις")
    }

    @Test
    fun `a row opens its profile`() {
        var opened: String? = null
        show(onOpen = { opened = it })
        rule.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("profile_chatter"))
        rule.onNodeWithTag("profile_chatter").performClick()
        assertEquals("chatter", opened)
    }

    @Test
    fun `levels 2 and 3 - layout and accessibility, with large numbers`() {
        rule.setContent {
            PestoTheme { ProfilesScreen(ProfilesState(rows.map { it.copy(apps = 1234, dictations = 56789) }, loading = false), onOpenProfile = {}) }
        }
        rule.assertLayout()
        rule.assertLeftAligned("profile_raw", "profile_note")
        rule.assertAccessible()
    }
}
