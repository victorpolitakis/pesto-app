package com.victorpolitakis.pesto

import androidx.lifecycle.ViewModel
import com.victorpolitakis.pesto.nav.SettingsSection
import com.victorpolitakis.pesto.permissions.PermissionFlow
import com.victorpolitakis.pesto.permissions.Requirement
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Keeps the permission flow across rotations, so "asked in this visit" is not
 * forgotten, and passes C3's requests to the navigation (Activity → NavHost).
 */
class MainViewModel : ViewModel() {
    private var flow: PermissionFlow? = null
    private val settingsRequests = Channel<SettingsSection>(Channel.BUFFERED)

    /** The sections of Settings that C3 opens: the pairing (Δ24). */
    val openSettings: Flow<SettingsSection> = settingsRequests.receiveAsFlow()

    fun requestSettings(section: SettingsSection) {
        settingsRequests.trySend(section)
    }

    /** A new flow when the order changed (the overlay method, Δ12): what was asked starts over. */
    fun flow(order: List<Requirement>): PermissionFlow =
        flow?.takeIf { it.order == order } ?: PermissionFlow(order).also { flow = it }

    fun onStop() {
        flow?.onStop()
    }
}
