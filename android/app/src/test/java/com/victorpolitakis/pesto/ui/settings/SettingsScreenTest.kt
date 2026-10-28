package com.victorpolitakis.pesto.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.nav.SettingsSection
import com.victorpolitakis.pesto.permissions.Requirement
import com.victorpolitakis.pesto.ui.assertAccessible
import com.victorpolitakis.pesto.ui.assertLayout
import com.victorpolitakis.pesto.ui.assertLeftAligned
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** C11: every section, what each control asks for, and the pairing without the token (Δ24). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val typed = mutableListOf<String>()

    private val actions = SettingsActions(
        onPrimaryLanguage = { calls += "primary $it" },
        onSecondaryLanguage = { calls += "secondary $it" },
        onStrictLanguages = { calls += "strict $it" },
        onDefaultProfile = { calls += "default $it" },
        onOverlayMethod = { calls += "overlay $it" },
        onRequest = { calls += "request $it" },
        onCheckBackend = { calls += "check" },
        onPair = { calls += "pair $it" },
        onUnpair = { calls += "unpair" },
        onAskDeleteAll = { calls += "ask delete" },
        onDeleteAll = { calls += "delete" },
        onDismissDeleteAll = { calls += "dismiss delete" },
    )

    private val state = SettingsState(
        permissions = listOf(
            RequirementStatus(Requirement.MICROPHONE, granted = true),
            RequirementStatus(Requirement.ACCESSIBILITY, granted = false),
            RequirementStatus(Requirement.BATTERY, granted = true),
        ),
        backendUrl = "ws://127.0.0.1:8000/v1/dictate",
        backend = BackendStatus.ANSWERS,
        paired = false,
        dictations = 3,
        version = "0.3.0 (1)",
        buildType = "debug",
        loading = false,
    )

    private fun show(s: SettingsState = state, section: SettingsSection? = null, code: String = "") {
        rule.setContent {
            PestoTheme { SettingsScreen(s, section, code, onCodeChange = { typed += it }, actions = actions, onBack = {}) }
        }
    }

    private fun scrollTo(tag: String) = rule.onNodeWithTag("settings").performScrollToNode(hasTestTag(tag))

    @Test
    fun `every section is there, and Xiaomi only on Xiaomi`() {
        show()
        for (s in listOf("languages", "profile", "permissions", "overlay", "backend", "privacy", "about")) {
            scrollTo("section_$s")
            rule.onNodeWithTag("section_$s").assertExists()
        }
        assertThrows(AssertionError::class.java) { scrollTo("section_xiaomi") }
    }

    @Test
    fun `the Xiaomi section opens the page of C3 again`() {
        show(state.copy(isXiaomi = true))
        scrollTo("xiaomi_open")
        rule.onNodeWithTag("xiaomi_open").performClick()
        assertEquals(listOf("request XIAOMI"), calls)
    }

    @Test
    fun `C3 opens Settings at the pairing`() {
        show(section = SettingsSection.BACKEND)
        rule.onNodeWithTag("section_backend").assertIsDisplayed()
        // Scrolled past the sections above it.
        rule.onNodeWithTag("section_languages").assertDoesNotExist()
    }

    @Test
    fun `the languages - primary, second without the primary, and strict`() {
        show()
        rule.onNodeWithTag("primary_language").assertTextContains("Ελληνικά").performClick()
        rule.onNodeWithTag("primary_language_de").performClick()
        rule.onNodeWithTag("secondary_language").assertTextContains("Αγγλικά").performClick()
        rule.onNodeWithTag("secondary_language_el").assertDoesNotExist()
        rule.onNodeWithTag("secondary_language_none").performClick()
        scrollTo("strict_languages")
        rule.onNodeWithTag("strict_languages").performClick()
        assertEquals(listOf("primary de", "secondary null", "strict true"), calls)
    }

    @Test
    fun `the default profile`() {
        show()
        scrollTo("default_raw")
        rule.onNodeWithTag("default_raw").assertIsSelected()
        scrollTo("default_mail")
        rule.onNodeWithTag("default_mail").performClick()
        assertEquals(listOf("default mail"), calls)
    }

    @Test
    fun `a missing permission has a button, a granted one says so`() {
        show()
        scrollTo("permission_accessibility")
        rule.onNodeWithTag("permission_accessibility").assertTextContains("Λείπει")
        rule.onNodeWithTag("request_accessibility").performClick()
        rule.onNodeWithTag("permission_microphone").assertTextContains("Εντάξει")
        rule.onNodeWithTag("request_microphone").assertDoesNotExist()
        assertEquals(listOf("request ACCESSIBILITY"), calls)
    }

    @Test
    fun `the overlay method, and what method 2 costs`() {
        show()
        scrollTo("overlay_application")
        rule.onNodeWithTag("overlay_accessibility").assertIsSelected()
        rule.onNodeWithTag("overlay_application")
            .assertTextContains("μόνιμη ειδοποίηση", substring = true)
            .performClick()
        assertEquals(listOf("overlay APPLICATION"), calls)
    }

    @Test
    fun `the backend - where it is, whether it answers, and the pairing code`() {
        show(section = SettingsSection.BACKEND)
        rule.onNodeWithTag("backend_url").assertTextContains("ws://127.0.0.1:8000/v1/dictate")
        rule.onNodeWithTag("backend_status").assertTextContains("Απαντά")
        rule.onNodeWithTag("check_backend").performClick()
        rule.onNodeWithTag("paired").assertTextContains("Όχι")
        rule.onNodeWithTag("pair").assertIsNotEnabled()
        rule.onNodeWithTag("pairing_code").performTextInput("1234")
        assertEquals("1234", typed.last())
        assertEquals(listOf("check"), calls)
    }

    @Test
    fun `eight digits pair`() {
        show(section = SettingsSection.BACKEND, code = "12345678")
        scrollTo("pair")
        rule.onNodeWithTag("pair").assertIsEnabled().performClick()
        assertEquals(listOf("pair 12345678"), calls)
    }

    @Test
    fun `a wrong code says so`() {
        show(state.copy(pairing = PairingStatus.WRONG_CODE), section = SettingsSection.BACKEND, code = "12345678")
        rule.onNodeWithTag("pairing_error", useUnmergedTree = true).assertTextContains("Λάθος ή ληγμένος κωδικός", substring = true)
    }

    @Test
    fun `a paired phone has no code field, and unpairs`() {
        show(state.copy(paired = true), section = SettingsSection.BACKEND)
        rule.onNodeWithTag("paired").assertTextContains("Ναι")
        rule.onNodeWithTag("pairing_code").assertDoesNotExist()
        scrollTo("unpair")
        rule.onNodeWithTag("unpair").performClick()
        assertEquals(listOf("unpair"), calls)
    }

    @Test
    fun `everything is deleted only after the confirmation`() {
        show()
        scrollTo("delete_all")
        rule.onNodeWithTag("history_size").assertTextContains("3 υπαγορεύσεις")
        rule.onNodeWithTag("delete_all").performClick()
        assertEquals(listOf("ask delete"), calls)
    }

    @Test
    fun `the confirmation of the deletion`() {
        show(state.copy(confirmDeleteAll = true))
        rule.onNodeWithTag("delete_all_dialog").assertIsDisplayed()
        rule.onNodeWithTag("delete_all_cancel").performClick()
        rule.onNodeWithTag("delete_all_confirm").performClick()
        assertEquals(listOf("dismiss delete", "delete"), calls)
    }

    @Test
    fun `an empty History has nothing to delete`() {
        show(state.copy(dictations = 0))
        scrollTo("delete_all")
        rule.onNodeWithTag("delete_all").assertIsNotEnabled()
    }

    @Test
    fun `about - the version and the build`() {
        show(section = SettingsSection.ABOUT)
        scrollTo("version")
        rule.onNodeWithTag("version").assertTextContains("0.3.0 (1)")
        rule.onNodeWithTag("build_type").assertTextContains("debug")
    }

    @Test
    fun `levels 2 and 3 - layout and accessibility, at the top and at the pairing`() {
        show(state.copy(isXiaomi = true, pairing = PairingStatus.WRONG_CODE), code = "1234")
        rule.assertLayout()
        rule.assertLeftAligned("primary_language", "secondary_language")
        rule.assertAccessible()
        scrollTo("pair")
        rule.assertLayout()
        rule.onRoot().tryPerformAccessibilityChecks()
    }
}
