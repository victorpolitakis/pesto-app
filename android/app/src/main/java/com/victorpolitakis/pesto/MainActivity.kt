package com.victorpolitakis.pesto

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.data.PairingRepository
import com.victorpolitakis.pesto.permissions.PermissionPrefs
import com.victorpolitakis.pesto.permissions.Requirement
import com.victorpolitakis.pesto.permissions.RequirementChecks
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import com.victorpolitakis.pesto.util.PestoLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * C3: on every open, asks only for what is missing, one at a time, with
 * Android's own dialogs and settings pages, and the pairing last (Δ24). No
 * onboarding screens: the rest is a plain status page until Home arrives.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var checks: RequirementChecks
    @Inject lateinit var prefs: PermissionPrefs
    @Inject lateinit var pairing: PairingRepository
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var log: PestoLog

    private val vm: MainViewModel by viewModels()
    // The result also triggers onResume, which asks for the next missing one.
    private val micRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) lifecycleScope.launch { prefs.setMicDenied() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PestoTheme {
                val status by vm.status.collectAsState()
                StatusScreen(status, status.granted.keys.toList())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            // The order is read every time: the overlay method may have changed (Δ12).
            val order = checks.order
            val missing = checks.missing(xiaomiShown = prefs.wasXiaomiShown(), paired = pairing.isPaired())
            val count = runCatching { history.count() }.getOrNull()
            vm.update {
                it.copy(granted = order.associateWith { r -> r !in missing }, backendUrl = BuildConfig.BACKEND_URL, dictations = count)
            }
            val next = vm.flow(order).next(missing) ?: return@launch
            log.event("permission request=$next missing=$missing")
            request(next)
        }
    }

    override fun onStop() {
        super.onStop()
        vm.onStop()
    }

    private fun request(r: Requirement) {
        when (r) {
            Requirement.MICROPHONE -> lifecycleScope.launch {
                // After a second "Deny" the system dialog no longer appears: only App info is left.
                if (prefs.wasMicDenied() && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    toast(R.string.toast_mic_settings)
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                } else {
                    micRequest.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            Requirement.ACCESSIBILITY -> {
                // TODO(future phase): a better way to guide the user than a toast.
                toast(if (checks.accessibilityEnabled()) R.string.toast_restart_accessibility else R.string.toast_enable_accessibility)
                open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Requirement.BATTERY ->
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()))
            Requirement.XIAOMI -> lifecycleScope.launch {
                prefs.setXiaomiShown()
                toast(R.string.toast_xiaomi)
                // The Xiaomi permission page; App info when it does not open (other HyperOS versions).
                val editor = Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", packageName)
                if (runCatching { startActivity(editor) }.isFailure) {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                }
            }
            Requirement.OVERLAY ->
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
            // Until the Settings page has the pairing field, the code comes from TestFieldsActivity (debug).
            Requirement.PAIRING -> toast(R.string.toast_pairing)
        }
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { log.event("open failed: ${intent.action}") }
    }

    private fun toast(@StringRes text: Int) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

@Composable
private fun StatusScreen(status: StatusState, order: List<Requirement>) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            for (r in order) {
                val ok = status.granted[r] == true
                Text((if (ok) "✔ " else "✘ ") + stringResource(labelOf(r)), Modifier.testTag("requirement_${r.name}"))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.status_backend, status.backendUrl), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.status_dictations, status.dictations ?: 0), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@StringRes
private fun labelOf(r: Requirement) = when (r) {
    Requirement.MICROPHONE -> R.string.req_microphone
    Requirement.ACCESSIBILITY -> R.string.req_accessibility
    Requirement.BATTERY -> R.string.req_battery
    Requirement.XIAOMI -> R.string.req_xiaomi
    Requirement.OVERLAY -> R.string.req_overlay
    Requirement.PAIRING -> R.string.req_pairing
}

@Preview
@Composable
private fun StatusPreview() {
    PestoTheme {
        StatusScreen(
            StatusState(mapOf(Requirement.MICROPHONE to true, Requirement.ACCESSIBILITY to false), "ws://127.0.0.1:8000/v1/dictate", 12),
            listOf(Requirement.MICROPHONE, Requirement.ACCESSIBILITY, Requirement.BATTERY),
        )
    }
}
