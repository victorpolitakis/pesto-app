package com.victorpolitakis.pesto.ui.settings

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.keyboard.ApkInstaller
import com.victorpolitakis.pesto.keyboard.KeyboardPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Android glue of the "Patch Keyboard" section (phase 4), kept out of the stateless section and
 * the ViewModel: the PackageInstaller flow (install, reinstall-over-a-foreign-key, and the revert
 * removal), its pending-user-action confirmation and unknown-sources permission, the keyboard
 * settings page, the keyboard picker and the Xiaomi autostart page. Device-only.
 */
@Composable
fun rememberPatchKeyboardActions(
    onBuild: () -> Unit,
    onInstallResult: (Int, String?) -> Unit,
    onRemoved: () -> Unit,
    onStartRevert: () -> Unit,
    onCancelRevert: () -> Unit,
    onXiaomiAutostartShown: () -> Unit,
    onClearError: () -> Unit,
): PatchKeyboardActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installer = remember { ApkInstaller(context) }

    // The install and uninstall sessions report back here; the "op" extra says which flow it was,
    // and a pending-user-action launches the system confirm.
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(ctx::startActivity)
                    return
                }
                when (intent.getStringExtra(EXTRA_OP)) {
                    OP_INSTALL -> onInstallResult(status, message)
                    OP_REINSTALL -> if (status == PackageInstaller.STATUS_SUCCESS) onBuild()
                    OP_REMOVE -> if (status == PackageInstaller.STATUS_SUCCESS) onRemoved()
                }
            }
        }
    }
    DisposableEffect(Unit) {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(INSTALL_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // Copying ~100 MB into the install session is heavy; keep it off the main thread.
    fun install(apk: String) = scope.launch(Dispatchers.IO) {
        runCatching { installer.install(File(apk), sender(context, OP_INSTALL)) }
            .onFailure { onInstallResult(PackageInstaller.STATUS_FAILURE, it.message) }
    }

    // Installing needs permission to install from this app; ask for it, then install on return.
    val pendingApk = remember { mutableStateOf<String?>(null) }
    val unknownSources = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val apk = pendingApk.value
        pendingApk.value = null
        if (apk != null && context.packageManager.canRequestPackageInstalls()) {
            install(apk)
        } else {
            Toast.makeText(context, R.string.patch_kbd_unknown_sources, Toast.LENGTH_LONG).show()
        }
    }

    return remember(onBuild, onInstallResult, onRemoved, onStartRevert, onCancelRevert, onXiaomiAutostartShown, onClearError) {
        PatchKeyboardActions(
            onBuild = onBuild,
            onInstall = { apk ->
                if (context.packageManager.canRequestPackageInstalls()) {
                    install(apk)
                } else {
                    pendingApk.value = apk
                    unknownSources.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                    )
                }
            },
            onReinstall = { installer.uninstall(sender(context, OP_REINSTALL)) },
            onEnableKeyboard = { runCatching { context.startActivity(installer.inputSettings()) } },
            onChooseKeyboard = { context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() },
            onXiaomiAutostart = {
                openXiaomiAutostart(context)
                onXiaomiAutostartShown()
            },
            onStartRevert = onStartRevert,
            onCancelRevert = onCancelRevert,
            onRemove = { installer.uninstall(sender(context, OP_REMOVE)) },
            onDismissError = onClearError,
        )
    }
}

private const val INSTALL_ACTION = "com.victorpolitakis.pesto.KEYBOARD_INSTALL_STATUS"
private const val EXTRA_OP = "op"
private const val OP_INSTALL = "install"
private const val OP_REINSTALL = "reinstall"
private const val OP_REMOVE = "remove"

/** A status callback that keeps the flow ("op") it belongs to. Distinct per op so results don't mix. */
private fun sender(context: Context, op: String): IntentSender =
    PendingIntent.getBroadcast(
        context,
        op.hashCode(),
        Intent(INSTALL_ACTION).setPackage(context.packageName).putExtra(EXTRA_OP, op),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    ).intentSender

private fun openXiaomiAutostart(context: Context) {
    // The HyperOS autostart editor, for the patched keyboard; app info when it does not open.
    val editor = Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", KeyboardPatch.PATCHED_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(editor) }.isFailure) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${KeyboardPatch.PATCHED_PACKAGE}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
