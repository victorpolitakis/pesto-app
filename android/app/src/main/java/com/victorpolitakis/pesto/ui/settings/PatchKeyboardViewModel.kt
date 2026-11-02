package com.victorpolitakis.pesto.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.keyboard.GboardInstall
import com.victorpolitakis.pesto.keyboard.KeyboardPatch
import com.victorpolitakis.pesto.keyboard.KeyboardPatchService
import com.victorpolitakis.pesto.keyboard.KeyboardStage
import com.victorpolitakis.pesto.keyboard.KeyboardStatus
import com.victorpolitakis.pesto.keyboard.LocalSigningKey
import com.victorpolitakis.pesto.keyboard.PatchAction
import com.victorpolitakis.pesto.permissions.PermissionPrefs
import com.victorpolitakis.pesto.permissions.RequirementChecks
import com.victorpolitakis.pesto.util.PestoLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** How the build is going, for the section. */
sealed interface BuildState {
    data object Idle : BuildState

    /** Running in the `:patcher` process; [step] is the name from PatchStep. */
    data class Building(val step: String) : BuildState

    /** The signed APK is ready at [apkPath]; the section installs it with PackageInstaller. */
    data class ReadyToInstall(val apkPath: String) : BuildState

    /** The build failed; [step] is where, for the log, with a short reason for the user. */
    data class Failed(val step: String?) : BuildState
}

data class PatchKeyboardState(
    val gboardInstalled: Boolean = true,
    val stage: KeyboardStage = KeyboardStage.NOT_INSTALLED,
    val stockStillEnabled: Boolean = false,
    val action: PatchAction = PatchAction.PATCH,
    val isXiaomi: Boolean = false,
    val build: BuildState = BuildState.Idle,
)

/**
 * Drives the "Patch Keyboard (Experimental)" section (phase 4). It binds [KeyboardPatchService] with
 * the application context and unbinds only on DONE or FAILED, so leaving the screen or a rotation
 * does not kill the build in the middle (the service ends its process when it unbinds). The stock
 * keyboard's languages are read when the build starts, while the stock keyboard is still enabled,
 * before the user is asked to disable it.
 *
 * It does not install: the section does that with PackageInstaller, which needs an activity.
 */
@HiltViewModel
class PatchKeyboardViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gboard: GboardInstall,
    private val keyboard: KeyboardStatus,
    private val signingKey: LocalSigningKey,
    private val checks: RequirementChecks,
    private val prefs: PermissionPrefs,
    private val log: PestoLog,
) : ViewModel() {
    private val _state = MutableStateFlow(PatchKeyboardState())
    val state: StateFlow<PatchKeyboardState> = _state.asStateFlow()

    private var connection: ServiceConnection? = null

    /** Read on every resume; the user grants and picks the keyboard on Android's own pages. */
    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val stock = gboard.versionCode(KeyboardPatch.GBOARD_PACKAGE)
                val action = stock?.let { gboard.planFor(it, signingKey.certificateSha256Bytes()) } ?: PatchAction.PATCH
                PatchKeyboardState(
                    gboardInstalled = stock != null,
                    stage = keyboard.stage(checks.isXiaomi, prefs.wasXiaomiShown()),
                    stockStillEnabled = keyboard.stockStillEnabled(),
                    action = action,
                    isXiaomi = checks.isXiaomi,
                    build = _state.value.build.takeIf { it !is BuildState.Idle } ?: BuildState.Idle,
                )
            }
            // Keep a build in progress; only its fields of the snapshot change.
            _state.update { current ->
                if (current.build is BuildState.Building || current.build is BuildState.ReadyToInstall) {
                    snapshot.copy(build = current.build)
                } else {
                    snapshot
                }
            }
        }
    }

    /** Build (or rebuild) the patched keyboard: bind the service, read languages, send the request. */
    fun build() {
        if (_state.value.build is BuildState.Building) return
        _state.update { it.copy(build = BuildState.Building(PatchStepFirst)) }
        viewModelScope.launch {
            // Read the stock keyboard's languages now, while it is still enabled (S2).
            val languages = withContext(Dispatchers.IO) { gboard.stockLanguagesFile() }
            bindAndBuild(languages)
        }
    }

    private fun bindAndBuild(languages: String?) {
        val replies = Messenger(Handler(Looper.getMainLooper(), ::onServiceMessage))
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val request = Message.obtain(null, KeyboardPatchService.MSG_BUILD).apply {
                    replyTo = replies
                    data = Bundle().apply { putString(KeyboardPatchService.KEY_LANGUAGES, languages) }
                }
                runCatching { Messenger(binder).send(request) }
                    .onFailure { fail(null) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // The :patcher process ended; if we were still building, treat it as a failure.
                if (_state.value.build is BuildState.Building) fail(null)
            }
        }
        connection = conn
        val intent = Intent(context, KeyboardPatchService::class.java)
        if (!context.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
            connection = null
            fail(null)
        }
    }

    private fun onServiceMessage(message: Message): Boolean {
        when (message.what) {
            KeyboardPatchService.MSG_STEP ->
                _state.update { it.copy(build = BuildState.Building(message.data?.getString(KeyboardPatchService.KEY_STEP) ?: PatchStepFirst)) }
            KeyboardPatchService.MSG_DONE -> {
                val apk = message.data?.getString(KeyboardPatchService.KEY_APK_PATH)
                _state.update { it.copy(build = if (apk != null) BuildState.ReadyToInstall(apk) else BuildState.Failed(null)) }
                unbind()
            }
            KeyboardPatchService.MSG_FAILED -> fail(message.data?.getString(KeyboardPatchService.KEY_STEP))
            else -> return false
        }
        return true
    }

    private fun fail(step: String?) {
        log.event("keyboard build failed step=$step")
        _state.update { it.copy(build = BuildState.Failed(step)) }
        unbind()
    }

    /** The section reports the install result; refresh the stage and clear the ready APK. */
    fun onInstallFinished() {
        deleteBuiltApk()
        _state.update { it.copy(build = BuildState.Idle) }
        refresh()
    }

    /** The section dismissed the error. */
    fun clearError() {
        _state.update { it.copy(build = BuildState.Idle) }
    }

    fun markXiaomiAutostartShown() {
        viewModelScope.launch {
            prefs.setXiaomiShown()
            refresh()
        }
    }

    private fun deleteBuiltApk() {
        (_state.value.build as? BuildState.ReadyToInstall)?.let { runCatching { java.io.File(it.apkPath).delete() } }
    }

    private fun unbind() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
    }

    override fun onCleared() {
        // A build in progress keeps running in its own process; only a finished build's binding
        // is released here.
        if (_state.value.build !is BuildState.Building) unbind()
    }

    private companion object {
        const val PatchStepFirst = "MERGING"
    }
}
