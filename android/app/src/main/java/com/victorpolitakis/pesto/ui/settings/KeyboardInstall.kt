package com.victorpolitakis.pesto.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.keyboard.ApkInstaller
import com.victorpolitakis.pesto.keyboard.KeyboardPatch
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * The Android glue of the "Patch Keyboard" section (phase 4), kept out of the stateless section and
 * the ViewModel: the PackageInstaller flow (with its pending-user-action confirmation and the
 * unknown-sources permission), the keyboard settings page, the keyboard picker, the Xiaomi autostart
 * page, and the uninstall. Device-only.
 */
@Composable
fun rememberPatchKeyboardActions(
    onBuild: () -> Unit,
    onInstallFinished: () -> Unit,
    onXiaomiAutostartShown: () -> Unit,
    onClearError: () -> Unit,
): PatchKeyboardActions {
    val context = LocalContext.current
    val installer = remember { ApkInstaller(context) }

    // The install session reports back here; a pending-user-action launches the system confirm.
    val sessionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(ctx::startActivity)
                    }
                    else -> onInstallFinished()
                }
            }
        }
    }
    DisposableEffect(Unit) {
        ContextCompat.registerReceiver(context, sessionReceiver, IntentFilter(INSTALL_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(sessionReceiver) } }
    }

    // Installing needs permission to install from this app; ask for it, then install on return.
    val pendingApk = remember { mutableStateOf<String?>(null) }
    val unknownSources = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val apk = pendingApk.value
        pendingApk.value = null
        if (apk != null && context.packageManager.canRequestPackageInstalls()) {
            installer.install(File(apk), installSender(context))
        } else {
            Toast.makeText(context, R.string.patch_kbd_unknown_sources, Toast.LENGTH_LONG).show()
        }
    }

    return remember(onBuild, onInstallFinished, onXiaomiAutostartShown, onClearError) {
        PatchKeyboardActions(
            onBuild = onBuild,
            onInstall = { apk ->
                if (context.packageManager.canRequestPackageInstalls()) {
                    installer.install(File(apk), installSender(context))
                } else {
                    pendingApk.value = apk
                    unknownSources.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                    )
                }
            },
            onEnableKeyboard = { runCatching { context.startActivity(installer.inputSettings()) } },
            onChooseKeyboard = {
                context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            },
            onXiaomiAutostart = {
                openXiaomiAutostart(context)
                onXiaomiAutostartShown()
            },
            onRevert = {
                // The instructions ask the user to enable and choose the normal Gboard first; then
                // the system's uninstall prompt removes the clone.
                runCatching { context.startActivity(installer.inputSettings()) }
                installer.uninstall(installSender(context))
            },
            onDismissError = onClearError,
        )
    }
}

private const val INSTALL_ACTION = "com.victorpolitakis.pesto.KEYBOARD_INSTALL_STATUS"

private fun installSender(context: Context) =
    android.app.PendingIntent.getBroadcast(
        context,
        0,
        Intent(INSTALL_ACTION).setPackage(context.packageName),
        android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
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
