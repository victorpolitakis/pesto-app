package com.victorpolitakis.pesto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.victorpolitakis.pesto.data.PairingRepository
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.nav.PestoNavHost
import com.victorpolitakis.pesto.nav.SettingsSection
import com.victorpolitakis.pesto.permissions.PermissionPrefs
import com.victorpolitakis.pesto.permissions.RequirementActions
import com.victorpolitakis.pesto.permissions.RequirementChecks
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import com.victorpolitakis.pesto.util.PestoLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * C3: on every open, asks only for what is missing, one at a time, with
 * Android's own dialogs and settings pages, and the pairing last (Δ24). No
 * onboarding screens and no warnings in the UI. Then the app: PestoNavHost.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var checks: RequirementChecks
    @Inject lateinit var prefs: PermissionPrefs
    @Inject lateinit var pairing: PairingRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var log: PestoLog

    private val vm: MainViewModel by viewModels()
    private lateinit var actions: RequirementActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actions = RequirementActions(this, prefs, checks, log, openPairing = { vm.requestSettings(SettingsSection.BACKEND) })
        // A new overlay method changes what C3 asks for (Δ12): asked at once, not at the next open.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                settings.settings.map { it.overlayMethod }.distinctUntilChanged().drop(1).collect { askNext() }
            }
        }
        setContent {
            PestoTheme {
                PestoNavHost(openSettings = vm.openSettings, onRequest = actions::request)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { askNext() }
    }

    override fun onStop() {
        super.onStop()
        vm.onStop()
    }

    /** The first missing requirement, never one already asked in this visit. The order is read every time. */
    private suspend fun askNext() {
        val order = checks.order
        val missing = checks.missing(xiaomiShown = prefs.wasXiaomiShown(), paired = pairing.isPaired())
        val next = vm.flow(order).next(missing) ?: return
        log.event("permission request=$next missing=$missing")
        actions.request(next)
    }
}
