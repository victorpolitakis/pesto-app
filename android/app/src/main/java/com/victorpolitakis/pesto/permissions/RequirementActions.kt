package com.victorpolitakis.pesto.permissions

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.util.PestoLog
import kotlinx.coroutines.launch

/**
 * What C3 and Settings do to get a requirement: Android's own dialogs and
 * settings pages, no screens of ours. Made in the Activity's onCreate: the
 * microphone dialog needs a launcher registered before the Activity starts.
 * [openPairing]: the pairing is ours, in Settings (Δ24).
 */
class RequirementActions(
    private val activity: ComponentActivity,
    private val prefs: PermissionPrefs,
    private val checks: RequirementChecks,
    private val log: PestoLog,
    private val openPairing: () -> Unit,
) {
    // The result also triggers onResume, which asks for the next missing one.
    private val micRequest = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) activity.lifecycleScope.launch { prefs.setMicDenied() }
    }

    private val packageUri get() = "package:${activity.packageName}".toUri()

    fun request(r: Requirement) {
        when (r) {
            Requirement.MICROPHONE -> activity.lifecycleScope.launch {
                // After a second "Deny" the system dialog no longer appears: only App info is left.
                if (prefs.wasMicDenied() && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    toast(R.string.toast_mic_settings)
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
                } else {
                    micRequest.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            Requirement.ACCESSIBILITY -> {
                // TODO(later): a better way to guide the user than a toast.
                toast(if (checks.accessibilityEnabled()) R.string.toast_restart_accessibility else R.string.toast_enable_accessibility)
                open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Requirement.BATTERY ->
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))
            Requirement.XIAOMI -> activity.lifecycleScope.launch {
                prefs.setXiaomiShown()
                toast(R.string.toast_xiaomi)
                // The Xiaomi permission page; App info when it does not open (other HyperOS versions).
                val editor = Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", activity.packageName)
                if (runCatching { activity.startActivity(editor) }.isFailure) {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
                }
            }
            Requirement.OVERLAY ->
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
            Requirement.PAIRING -> openPairing()
        }
    }

    private fun open(intent: Intent) {
        runCatching { activity.startActivity(intent) }.onFailure { log.event("open failed: ${intent.action}") }
    }

    private fun toast(@StringRes text: Int) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
}

/** The name of a requirement, in Settings. */
@StringRes
fun labelOf(r: Requirement): Int = when (r) {
    Requirement.MICROPHONE -> R.string.req_microphone
    Requirement.ACCESSIBILITY -> R.string.req_accessibility
    Requirement.BATTERY -> R.string.req_battery
    Requirement.XIAOMI -> R.string.req_xiaomi
    Requirement.OVERLAY -> R.string.req_overlay
    Requirement.PAIRING -> R.string.req_pairing
}
