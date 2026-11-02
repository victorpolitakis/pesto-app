package com.victorpolitakis.pesto.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.keyboard.BuildState
import com.victorpolitakis.pesto.keyboard.KeyboardStage
import com.victorpolitakis.pesto.keyboard.PatchAction
import com.victorpolitakis.pesto.keyboard.RevertStep
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Patch Keyboard section: each state shows its step and its button calls the right action. The
 * v2 rule queues the composition on a StandardTestDispatcher, so [show] and the clicks wait for idle.
 */
@RunWith(AndroidJUnit4::class)
class PatchKeyboardSectionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val actions = PatchKeyboardActions(
        onBuild = { calls += "build" },
        onInstall = { calls += "install $it" },
        onReinstall = { calls += "reinstall" },
        onEnableKeyboard = { calls += "enable" },
        onChooseKeyboard = { calls += "choose" },
        onXiaomiAutostart = { calls += "xiaomi" },
        onStartRevert = { calls += "revert" },
        onCancelRevert = { calls += "cancel" },
        onRemove = { calls += "remove" },
        onDismissError = { calls += "dismiss" },
    )

    private fun show(state: PatchKeyboardState) {
        rule.setContent { PestoTheme { PatchKeyboardSection(state, actions) } }
        rule.waitForIdle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `no gboard shows only the note`() {
        show(PatchKeyboardState(gboardInstalled = false))
        rule.onNodeWithTag("patch_kbd_patch").assertDoesNotExist()
    }

    @Test
    fun `not installed offers to patch`() {
        show(PatchKeyboardState(stage = KeyboardStage.NOT_INSTALLED))
        click("patch_kbd_patch")
        assertEquals(listOf("build"), calls)
    }

    @Test
    fun `a foreign-key clone offers reinstall`() {
        show(PatchKeyboardState(stage = KeyboardStage.NEEDS_REINSTALL))
        click("patch_kbd_reinstall")
        assertEquals(listOf("reinstall"), calls)
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
        click("patch_kbd_install")
        assertEquals(listOf("install /data/pesto/kbd.apk"), calls)
    }

    @Test
    fun `failed shows the error and dismisses`() {
        show(PatchKeyboardState(build = BuildState.Failed("install")))
        rule.onNodeWithTag("patch_kbd_failed").assertIsDisplayed()
        click("patch_kbd_dismiss")
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `installed not enabled opens keyboard settings`() {
        show(PatchKeyboardState(stage = KeyboardStage.INSTALLED_NOT_ENABLED))
        click("patch_kbd_enable")
        assertEquals(listOf("enable"), calls)
    }

    @Test
    fun `enabled not current opens the picker`() {
        show(PatchKeyboardState(stage = KeyboardStage.ENABLED_NOT_CURRENT))
        click("patch_kbd_choose")
        assertEquals(listOf("choose"), calls)
    }

    @Test
    fun `xiaomi autostart step`() {
        show(PatchKeyboardState(stage = KeyboardStage.XIAOMI_AUTOSTART, isXiaomi = true))
        click("patch_kbd_xiaomi")
        assertEquals(listOf("xiaomi"), calls)
    }

    @Test
    fun `active starts revert, and shows update only when one is available`() {
        show(PatchKeyboardState(stage = KeyboardStage.ACTIVE, action = PatchAction.UP_TO_DATE))
        rule.onNodeWithTag("patch_kbd_active").assertIsDisplayed()
        rule.onNodeWithTag("patch_kbd_update").assertDoesNotExist()
        click("patch_kbd_revert")
        assertEquals(listOf("revert"), calls)
    }

    @Test
    fun `active with an update offers update`() {
        show(PatchKeyboardState(stage = KeyboardStage.ACTIVE, action = PatchAction.UPDATE))
        click("patch_kbd_update")
        assertEquals(listOf("build"), calls)
    }

    @Test
    fun `active with the stock keyboard still on offers to open keyboard settings`() {
        show(PatchKeyboardState(stage = KeyboardStage.ACTIVE, stockStillEnabled = true))
        click("patch_kbd_disable_stock")
        assertEquals(listOf("enable"), calls)
    }

    @Test
    fun `revert enables the stock keyboard first`() {
        show(PatchKeyboardState(reverting = true, revertStep = RevertStep.ENABLE_STOCK))
        click("patch_kbd_revert_enable")
        assertEquals(listOf("enable"), calls)
    }

    @Test
    fun `revert then removes the clone`() {
        show(PatchKeyboardState(reverting = true, revertStep = RevertStep.REMOVE))
        click("patch_kbd_remove")
        assertEquals(listOf("remove"), calls)
    }

    @Test
    fun `revert can be cancelled`() {
        show(PatchKeyboardState(reverting = true, revertStep = RevertStep.ENABLE_STOCK))
        click("patch_kbd_cancel_revert")
        assertEquals(listOf("cancel"), calls)
    }
}
