package com.victorpolitakis.pesto.keyboard

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Installs and removes the patched keyboard with [PackageInstaller] (phase 3). The system shows the
 * user the install and uninstall prompts; Pesto never installs silently. Needs
 * REQUEST_INSTALL_PACKAGES and REQUEST_DELETE_PACKAGES, which Settings asks for only when the
 * switch is turned on (phase 4).
 */
class ApkInstaller @Inject constructor(@param:ApplicationContext private val context: Context) {
    /**
     * Writes [apk] into a new install session and commits it. [statusReceiver] is the
     * [IntentSender] the system reports the result to (and shows the confirmation through); Settings
     * provides it from a broadcast it listens on. Returns the session id.
     */
    fun install(apk: File, statusReceiver: IntentSender): Int {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(KeyboardPatch.PATCHED_PACKAGE)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            session.commit(statusReceiver)
        }
        return sessionId
    }

    /** Asks the system to uninstall the patched keyboard, which prompts the user. */
    fun uninstall(statusReceiver: IntentSender) {
        context.packageManager.packageInstaller.uninstall(KeyboardPatch.PATCHED_PACKAGE, statusReceiver)
    }

    /** Opens the patched keyboard's system settings page, e.g. for the user to turn it on. */
    fun inputSettings(): Intent = Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
