package com.victorpolitakis.pesto.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.keyboard.BuildState
import com.victorpolitakis.pesto.keyboard.GboardInstall
import com.victorpolitakis.pesto.keyboard.KeyboardBuild
import com.victorpolitakis.pesto.keyboard.KeyboardPatch
import com.victorpolitakis.pesto.keyboard.KeyboardStage
import com.victorpolitakis.pesto.keyboard.KeyboardStatus
import com.victorpolitakis.pesto.keyboard.LocalSigningKey
import com.victorpolitakis.pesto.keyboard.PatchAction
import com.victorpolitakis.pesto.keyboard.RevertStep
import com.victorpolitakis.pesto.permissions.PermissionPrefs
import com.victorpolitakis.pesto.permissions.RequirementChecks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PatchKeyboardState(
    val gboardInstalled: Boolean = true,
    val stage: KeyboardStage = KeyboardStage.NOT_INSTALLED,
    val stockStillEnabled: Boolean = false,
    val action: PatchAction = PatchAction.PATCH,
    val isXiaomi: Boolean = false,
    val build: BuildState = BuildState.Idle,
    /** The user started the revert flow. */
    val reverting: Boolean = false,
    /** The current revert step while [reverting]. */
    val revertStep: RevertStep? = null,
)

/**
 * Drives the "Patch Keyboard (Experimental)" section (phase 4). The build runs in
 * [KeyboardBuild] (application-scoped), so leaving the screen does not drop it; this ViewModel reads
 * the device state and the build state and combines them. The stock keyboard's languages are read
 * when the build starts, while the stock keyboard is still enabled, and remembered, so an update or
 * a reinstall with the stock keyboard already off still gets them.
 *
 * It does not install or uninstall: the section does that with PackageInstaller, which needs an
 * activity, and reports the result back here.
 */
@HiltViewModel
class PatchKeyboardViewModel @Inject constructor(
    private val gboard: GboardInstall,
    private val keyboard: KeyboardStatus,
    private val signingKey: LocalSigningKey,
    private val checks: RequirementChecks,
    private val prefs: PermissionPrefs,
    private val keyboardBuild: KeyboardBuild,
) : ViewModel() {
    private data class Snapshot(
        val gboardInstalled: Boolean = true,
        val stage: KeyboardStage = KeyboardStage.NOT_INSTALLED,
        val stockStillEnabled: Boolean = false,
        val action: PatchAction = PatchAction.PATCH,
        val isXiaomi: Boolean = false,
        val revertStep: RevertStep? = null,
    )

    private val snapshot = MutableStateFlow(Snapshot())
    private val reverting = MutableStateFlow(false)

    val state: StateFlow<PatchKeyboardState> =
        combine(snapshot, keyboardBuild.state, reverting) { snap, build, rev ->
            PatchKeyboardState(
                gboardInstalled = snap.gboardInstalled,
                stage = snap.stage,
                stockStillEnabled = snap.stockStillEnabled,
                action = snap.action,
                isXiaomi = snap.isXiaomi,
                build = build,
                reverting = rev,
                revertStep = if (rev) snap.revertStep else null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatchKeyboardState())

    /** Read on every resume; the user grants and picks the keyboard on Android's own pages. */
    fun refresh() {
        viewModelScope.launch {
            snapshot.value = withContext(Dispatchers.IO) {
                val stock = gboard.versionCode(KeyboardPatch.GBOARD_PACKAGE)
                val action = stock?.let { gboard.planFor(it, signingKey.certificateSha256Bytes()) } ?: PatchAction.PATCH
                Snapshot(
                    gboardInstalled = stock != null,
                    stage = keyboard.stage(checks.isXiaomi, prefs.wasKeyboardAutostartShown()),
                    stockStillEnabled = keyboard.stockStillEnabled(),
                    action = action,
                    isXiaomi = checks.isXiaomi,
                    revertStep = keyboard.revertStep(),
                )
            }
        }
    }

    /** Build (or rebuild) the patched keyboard: read the languages, then run the build. */
    fun build() {
        viewModelScope.launch {
            val languages = withContext(Dispatchers.IO) {
                // Prefer the stock keyboard's current languages; remember them for later builds.
                val stock = gboard.stockLanguagesFile()
                if (stock != null) prefs.setLastKeyboardLanguages(stock)
                // When the stock keyboard is already off, reuse the last ones (fix 7).
                stock ?: prefs.lastKeyboardLanguages()
            }
            keyboardBuild.build(languages)
        }
    }

    /** The install finished: feed the result to the build, then re-read the stage. */
    fun onInstallResult(status: Int, message: String?) {
        keyboardBuild.onInstallResult(status, message)
        refresh()
    }

    fun clearError() = keyboardBuild.clearError()

    fun startRevert() {
        reverting.value = true
        refresh()
    }

    fun cancelRevert() {
        reverting.value = false
    }

    /** The clone was removed during revert: leave the revert flow and re-read. */
    fun onRemoved() {
        reverting.value = false
        refresh()
    }

    fun markKeyboardAutostartShown() {
        viewModelScope.launch {
            prefs.setKeyboardAutostartShown()
            refresh()
        }
    }
}
