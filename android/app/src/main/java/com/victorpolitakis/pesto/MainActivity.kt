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
import com.victorpolitakis.pesto.accessibility.EditorBridge
import com.victorpolitakis.pesto.data.HistoryRepository
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
 * Android's own dialogs and settings pages. No onboarding screens: the rest
 * is a plain status page until Home arrives in phase 3.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var bridge: EditorBridge
    @Inject lateinit var prefs: PermissionPrefs
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var log: PestoLog

    private val vm: MainViewModel by viewModels()
    private lateinit var checks: RequirementChecks
    // The result also triggers onResume, which asks for the next missing one.
    private val micRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The overlay permission only while the debug build compares the two overlay methods (Δ16).
        checks = RequirementChecks(this, bridge, withOverlay = BuildConfig.DEBUG)
        setContent {
            PestoTheme {
                val status by vm.status.collectAsState()
                StatusScreen(status, checks.order)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val missing = checks.missing()
        vm.update { it.copy(granted = checks.order.associateWith { r -> r !in missing }, backendUrl = BuildConfig.BACKEND_URL) }
        lifecycleScope.launch {
            val count = runCatching { history.count() }.getOrNull()
            vm.update { it.copy(dictations = count) }
        }
        val next = vm.flow(checks.order).next(missing) ?: return
        log.event("permission request=$next missing=$missing")
        request(next)
    }

    override fun onStop() {
        super.onStop()
        vm.flow(checks.order).onStop()
    }

    private fun request(r: Requirement) {
        when (r) {
            Requirement.MICROPHONE -> lifecycleScope.launch {
                // After a second "Deny" the system dialog no longer appears: only App info is left.
                if (prefs.wasMicAsked() && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    toast(R.string.toast_mic_settings)
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                } else {
                    prefs.setMicAsked()
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
            Requirement.OVERLAY ->
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
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
    Requirement.OVERLAY -> R.string.req_overlay
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
