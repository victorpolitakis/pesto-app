package com.victorpolitakis.pesto.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.keyboard.BuildState
import com.victorpolitakis.pesto.keyboard.KeyboardStage
import com.victorpolitakis.pesto.keyboard.PatchAction
import com.victorpolitakis.pesto.keyboard.RevertStep

/** What the section asks for; the Route gives the functions, the tests their own. */
class PatchKeyboardActions(
    /** Build (or rebuild) the patched keyboard. */
    val onBuild: () -> Unit = {},
    /** Install the built APK at the given path (PackageInstaller). */
    val onInstall: (String) -> Unit = {},
    /** Remove a clone signed with another key, then build again. */
    val onReinstall: () -> Unit = {},
    /** Open the system keyboard list (to enable the patched keyboard, or the normal Gboard). */
    val onEnableKeyboard: () -> Unit = {},
    /** Open the keyboard picker (to choose the patched keyboard, or the normal Gboard). */
    val onChooseKeyboard: () -> Unit = {},
    /** Open the Xiaomi autostart page for the patched keyboard, and mark it shown. */
    val onXiaomiAutostart: () -> Unit = {},
    /** Start the revert flow. */
    val onStartRevert: () -> Unit = {},
    /** Leave the revert flow. */
    val onCancelRevert: () -> Unit = {},
    /** Remove the patched keyboard (the last revert step). */
    val onRemove: () -> Unit = {},
    /** Dismiss a build error. */
    val onDismissError: () -> Unit = {},
)

/**
 * Settings → "Patch Keyboard (Experimental)" (phase 4). A checklist of the steps to a working
 * patched keyboard, and a revert flow that puts the normal Gboard back before removing the clone.
 * No styling; design is phase 6.
 */
@Composable
fun PatchKeyboardSection(state: PatchKeyboardState, actions: PatchKeyboardActions, modifier: Modifier = Modifier) {
    Section(stringResource(R.string.settings_patch_keyboard), "section_patch_keyboard", modifier) {
        Explanation(stringResource(R.string.settings_patch_keyboard_explained))

        if (!state.gboardInstalled) {
            Explanation(stringResource(R.string.patch_kbd_no_gboard))
            return@Section
        }
        if (state.reverting) {
            RevertFlow(state.revertStep, actions)
            return@Section
        }
        when (val build = state.build) {
            is BuildState.Building -> {
                Explanation(stringResource(stepLabel(build.step)), Modifier.testTag("patch_kbd_building"))
                return@Section
            }
            is BuildState.ReadyToInstall -> {
                Explanation(stringResource(R.string.patch_kbd_install_ready))
                Action("patch_kbd_install", R.string.patch_kbd_install) { actions.onInstall(build.apkPath) }
                return@Section
            }
            is BuildState.Failed -> {
                Explanation(stringResource(R.string.patch_kbd_failed), Modifier.testTag("patch_kbd_failed"))
                Action("patch_kbd_dismiss", R.string.patch_kbd_dismiss, actions.onDismissError)
                return@Section
            }
            BuildState.Idle -> Unit
        }

        when (state.stage) {
            KeyboardStage.NOT_INSTALLED ->
                Action("patch_kbd_patch", R.string.patch_kbd_patch, actions.onBuild)

            KeyboardStage.NEEDS_REINSTALL -> {
                Step(stringResource(R.string.patch_kbd_reinstall))
                Action("patch_kbd_reinstall", R.string.patch_kbd_reinstall_open, actions.onReinstall)
            }

            KeyboardStage.INSTALLED_NOT_ENABLED -> {
                Step(stringResource(R.string.patch_kbd_enable))
                Action("patch_kbd_enable", R.string.patch_kbd_enable_open, actions.onEnableKeyboard)
            }

            KeyboardStage.ENABLED_NOT_CURRENT -> {
                Step(stringResource(R.string.patch_kbd_choose))
                Action("patch_kbd_choose", R.string.patch_kbd_choose_open, actions.onChooseKeyboard)
            }

            KeyboardStage.XIAOMI_AUTOSTART -> {
                Step(stringResource(R.string.patch_kbd_xiaomi))
                Action("patch_kbd_xiaomi", R.string.patch_kbd_xiaomi_open, actions.onXiaomiAutostart)
            }

            KeyboardStage.ACTIVE -> {
                Explanation(stringResource(R.string.patch_kbd_active), Modifier.testTag("patch_kbd_active"))
                if (state.stockStillEnabled) {
                    Explanation(stringResource(R.string.patch_kbd_stock_enabled))
                    Action("patch_kbd_disable_stock", R.string.patch_kbd_enable_open, actions.onEnableKeyboard)
                }
                if (state.action == PatchAction.UPDATE) {
                    Explanation(stringResource(R.string.patch_kbd_update_available))
                    Action("patch_kbd_update", R.string.patch_kbd_update, actions.onBuild)
                }
                Explanation(stringResource(R.string.patch_kbd_revert_explained))
                Action("patch_kbd_revert", R.string.patch_kbd_revert, actions.onStartRevert)
            }
        }
    }
}

@Composable
private fun RevertFlow(step: RevertStep?, actions: PatchKeyboardActions) {
    when (step) {
        RevertStep.ENABLE_STOCK -> {
            Step(stringResource(R.string.patch_kbd_revert_enable_stock))
            Action("patch_kbd_revert_enable", R.string.patch_kbd_enable_open, actions.onEnableKeyboard)
        }
        RevertStep.CHOOSE_STOCK -> {
            Step(stringResource(R.string.patch_kbd_revert_choose_stock))
            Action("patch_kbd_revert_choose", R.string.patch_kbd_choose_open, actions.onChooseKeyboard)
        }
        // Null while the device state is being read; show the last step's intent (remove).
        RevertStep.REMOVE, null -> {
            Step(stringResource(R.string.patch_kbd_revert_remove))
            Action("patch_kbd_remove", R.string.patch_kbd_revert_remove_open, actions.onRemove)
        }
    }
    TextButton(onClick = actions.onCancelRevert, modifier = Modifier.padding(horizontal = 16.dp).testTag("patch_kbd_cancel_revert")) {
        Text(stringResource(R.string.patch_kbd_cancel))
    }
}

@Composable
private fun Step(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Action(tag: String, textRes: Int, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag(tag)) {
        Text(stringResource(textRes))
    }
}

/** The progress label for a PatchStep name; an unknown step shows the generic "Building…". */
private fun stepLabel(step: String): Int = when (step) {
    "MERGING" -> R.string.patch_kbd_step_merging
    "PATCHING" -> R.string.patch_kbd_step_patching
    "SIGNING" -> R.string.patch_kbd_step_signing
    else -> R.string.patch_kbd_building
}
