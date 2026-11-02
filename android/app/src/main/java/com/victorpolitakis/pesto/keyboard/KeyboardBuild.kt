package com.victorpolitakis.pesto.keyboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.victorpolitakis.pesto.util.PestoLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** How building and installing the patched keyboard is going; shared, so it survives the screen. */
sealed interface BuildState {
    data object Idle : BuildState

    /** Running in the `:patcher` process; [step] is the name from PatchStep. */
    data class Building(val step: String) : BuildState

    /** The signed APK is ready at [apkPath]; the section installs it with PackageInstaller. */
    data class ReadyToInstall(val apkPath: String) : BuildState

    /** The build or install failed; [reason] is a short code for the log, not for the user. */
    data class Failed(val reason: String?) : BuildState
}

/**
 * Holds the connection to [KeyboardPatchService] and the [BuildState] in the application graph
 * (phase 4, fix 6). A build runs in the service's own `:patcher` process; keeping its state here,
 * not in a ViewModel, means leaving the Settings screen or a rotation does not drop the binding or
 * lose the result. The ViewModel observes [state] and starts a build; the install result is fed
 * back here too, so the one place knows where things stand.
 */
@Singleton
class KeyboardBuild @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val log: PestoLog,
) {
    private val _state = MutableStateFlow<BuildState>(BuildState.Idle)
    val state: StateFlow<BuildState> = _state.asStateFlow()

    private val replies = Messenger(Handler(Looper.getMainLooper(), ::onServiceMessage))
    private var connection: ServiceConnection? = null

    /** Start a build with [languages] (the pesto_locales.txt contents, or null). Ignored if one runs. */
    @Synchronized
    fun build(languages: String?) {
        if (_state.value is BuildState.Building) return
        _state.value = BuildState.Building(FIRST_STEP)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val request = Message.obtain(null, KeyboardPatchService.MSG_BUILD).apply {
                    replyTo = replies
                    data = Bundle().apply { putString(KeyboardPatchService.KEY_LANGUAGES, languages) }
                }
                runCatching { Messenger(binder).send(request) }.onFailure { fail("send") }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (_state.value is BuildState.Building) fail("disconnected")
            }
        }
        connection = conn
        if (!context.bindService(Intent(context, KeyboardPatchService::class.java), conn, Context.BIND_AUTO_CREATE)) {
            connection = null
            fail("bind")
        }
    }

    private fun onServiceMessage(message: Message): Boolean {
        when (message.what) {
            KeyboardPatchService.MSG_STEP ->
                _state.update { BuildState.Building(message.data?.getString(KeyboardPatchService.KEY_STEP) ?: FIRST_STEP) }
            KeyboardPatchService.MSG_DONE -> {
                val apk = message.data?.getString(KeyboardPatchService.KEY_APK_PATH)
                _state.value = if (apk != null) BuildState.ReadyToInstall(apk) else BuildState.Failed("no_apk")
                unbind()
            }
            KeyboardPatchService.MSG_FAILED -> fail(message.data?.getString(KeyboardPatchService.KEY_STEP))
            else -> return false
        }
        return true
    }

    /** The install finished with [status] (PackageInstaller.STATUS_*) and an optional [message]. */
    fun onInstallResult(status: Int, message: String?) {
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> installed()
            // The user cancelled the system prompt: keep the built APK so they can try again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                log.event("keyboard install failed status=$status message=$message")
                _state.value = BuildState.Failed("install")
            }
        }
    }

    /** A successful install: drop the built APK and go idle; the ViewModel then re-reads the stage. */
    private fun installed() {
        (_state.value as? BuildState.ReadyToInstall)?.let { runCatching { File(it.apkPath).delete() } }
        _state.value = BuildState.Idle
    }

    fun clearError() {
        if (_state.value is BuildState.Failed) _state.value = BuildState.Idle
    }

    private fun fail(reason: String?) {
        log.event("keyboard build failed step=$reason")
        _state.value = BuildState.Failed(reason)
        unbind()
    }

    private fun unbind() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
    }

    private companion object {
        const val FIRST_STEP = "MERGING"
    }
}
