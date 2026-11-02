package com.victorpolitakis.pesto.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.keyboard.KeyboardStage
import com.victorpolitakis.pesto.keyboard.PatchAction
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Patch Keyboard section: each state shows its step and its button calls the right action. */
@RunWith(AndroidJUnit4::class)
class PatchKeyboardSectionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val actions = PatchKeyboardActions(
        onBuild = { calls += "build" },
        onInstall = { calls += "install $it" },
        onEnableKeyboard = { calls += "enable" },
        onChooseKeyboard = { calls += "choose" },
        onXiaomiAutostart = { calls += "xiaomi" },
        onRevert = { calls += "revert" },
        onDismissError = { calls += "dismiss" },
    )

    private fun show(state: PatchKeyboardState) {
        rule.setContent { PestoTheme { PatchKeyboardSection(state, actions) } }
    }

    @Test
    fun `no gboard shows only the note`() {
        show(PatchKeyboardState(gboardInstalled = false))
        rule.onNodeWithTag("patch_kbd_patch").assertDoesNotExist()
    }

    @Test
    fun `not installed offers to patch`() {
        show(PatchKeyboardState(stage = KeyboardStage.NOT_INSTALLED))
        rule.onNodeWithTag("patch_kbd_patch").assertIsDisplayed().performClick()
        assertEquals(listOf("build"), calls)
    }

    @Test
    fun `building shows the step and no buttons`() {
        show(PatchKeyboardState(build = BuildState.Building("PATCHING")))
        rule.onNodeWithTag("patch_kbd_building").assertIsDisplayed()
        rule.onNodeWithTag("patch_kbd_patch").assertDoesNotExist()
    }

    @Test
    fun `ready to install installs the built apk`() {
        show(PatchKeyboardState(build = BuildState.ReadyToInstall("/data/pesto/kbd.apk")))
        rule.onNodeWithTag("patch_kbd_install").performClick()
        assertEquals(listOf("install /data/pesto/kbd.apk"), calls)
    }

    @Test
    fun `failed shows the error and dismisses`() {
        show(PatchKeyboardState(build = BuildState.Failed("PATCHING")))
        rule.onNodeWithTag("patch_kbd_failed").assertIsDisplayed()
        rule.onNodeWithTag("patch_kbd_dismiss").performClick()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `installed not enabled opens keyboard settings`() {
        show(PatchKeyboardState(stage = KeyboardStage.INSTALLED_NOT_ENABLED))
        rule.onNodeWithTag("patch_kbd_enable").performClick()
        assertEquals(listOf("enable"), calls)
    }

    @Test
    fun `enabled not current opens the picker`() {
        show(PatchKeyboardState(stage = KeyboardStage.ENABLED_NOT_CURRENT))
        rule.onNodeWithTag("patch_kbd_choose").performClick()
        assertEquals(listOf("choose"), calls)
    }

    @Test
    fun `xiaomi autostart step`() {
        show(PatchKeyboardState(stage = KeyboardStage.XIAOMI_AUTOSTART, isXiaomi = true))
        rule.onNodeWithTag("patch_kbd_xiaomi").performClick()
        assertEquals(listOf("xiaomi"), calls)
    }

    @Test
    fun `active offers revert, and update only when one is available`() {
        show(PatchKeyboardState(stage = KeyboardStage.ACTIVE, action = PatchAction.UP_TO_DATE))
        rule.onNodeWithTag("patch_kbd_active").assertIsDisplayed()
        rule.onNodeWithTag("patch_kbd_update").assertDoesNotExist()
        rule.onNodeWithTag("patch_kbd_revert").performClick()
        assertEquals(listOf("revert"), calls)
    }

    @Test
    fun `active with an update offers update`() {
        show(PatchKeyboardState(stage = KeyboardStage.ACTIVE, action = PatchAction.UPDATE))
        rule.onNodeWithTag("patch_kbd_update").performClick()
        assertEquals(listOf("build"), calls)
    }
}
