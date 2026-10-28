package com.victorpolitakis.pesto.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.BuildConfig
import com.victorpolitakis.pesto.backend.PairResult
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.data.PairingRepository
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.SettingsRepository
import com.victorpolitakis.pesto.overlay.OverlayMethod
import com.victorpolitakis.pesto.permissions.PermissionPrefs
import com.victorpolitakis.pesto.permissions.Requirement
import com.victorpolitakis.pesto.permissions.RequirementChecks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings (C11). What Android granted is read again on every resume
 * ([refresh]), since the user grants it on Android's own pages.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val profiles: ProfilesRepository,
    private val pairing: PairingRepository,
    private val history: HistoryRepository,
    private val checks: RequirementChecks,
    private val prefs: PermissionPrefs,
) : ViewModel() {
    private val resumes = MutableStateFlow(0)
    private val backend = MutableStateFlow(BackendStatus.CHECKING)
    private val pairingStatus = MutableStateFlow(PairingStatus.IDLE)
    private val confirmDeleteAll = MutableStateFlow(false)

    /** The permissions of C3 for the method in the file (the Xiaomi page and the pairing have their own sections). */
    private val permissions = combine(settings.stored, resumes) { s, _ ->
        val xiaomiShown = prefs.wasXiaomiShown()
        val paired = pairing.isPaired()
        checks.order(s.overlayMethod)
            .filter { it in PERMISSIONS }
            .map { RequirementStatus(it, checks.isGranted(it, xiaomiShown, paired)) }
    }

    private val screen = combine(backend, pairingStatus, confirmDeleteAll) { b, p, c -> Triple(b, p, c) }

    val state: StateFlow<SettingsState> = combine(
        settings.stored,
        permissions,
        pairing.paired,
        history.total,
        screen,
    ) { s, granted, paired, dictations, (backendStatus, pairingResult, confirm) ->
        SettingsState(
            settings = s,
            permissions = granted,
            isXiaomi = checks.isXiaomi,
            backendUrl = BuildConfig.BACKEND_URL,
            backend = backendStatus,
            paired = paired,
            pairing = pairingResult,
            dictations = dictations,
            confirmDeleteAll = confirm,
            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            buildType = BuildConfig.BUILD_TYPE,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    /** On every resume: the permissions, and whether the backend answers. */
    fun refresh() {
        resumes.update { it + 1 }
        checkBackend()
    }

    fun checkBackend() {
        viewModelScope.launch {
            backend.value = BackendStatus.CHECKING
            backend.value = if (pairing.backendAnswers()) BackendStatus.ANSWERS else BackendStatus.NO_ANSWER
        }
    }

    fun setPrimaryLanguage(code: String) {
        viewModelScope.launch { settings.setPrimaryLanguage(code) }
    }

    fun setSecondaryLanguage(code: String?) {
        viewModelScope.launch { settings.setSecondaryLanguage(code) }
    }

    fun setStrictLanguages(strict: Boolean) {
        viewModelScope.launch { settings.setStrictLanguages(strict) }
    }

    fun setDefaultProfile(id: String) {
        viewModelScope.launch { profiles.setDefault(id) }
    }

    /** MainActivity follows the method and asks for the overlay permission at once (Δ12). */
    fun setOverlayMethod(method: OverlayMethod) {
        viewModelScope.launch { settings.setOverlayMethod(method) }
    }

    /** The code is never logged (PairingRepository logs only the result). */
    fun pair(code: String) {
        viewModelScope.launch {
            pairingStatus.value = PairingStatus.PAIRING
            pairingStatus.value = when (pairing.pair(code)) {
                is PairResult.Paired -> PairingStatus.PAIRED
                PairResult.WrongCode -> PairingStatus.WRONG_CODE
                is PairResult.Failed -> PairingStatus.FAILED
            }
        }
    }

    fun unpair() {
        viewModelScope.launch {
            pairing.unpair()
            pairingStatus.value = PairingStatus.IDLE
        }
    }

    fun askDeleteAll() {
        confirmDeleteAll.value = true
    }

    fun dismissDeleteAll() {
        confirmDeleteAll.value = false
    }

    /** Δ15: all of the History, after the confirmation, without an undo. */
    fun deleteAll() {
        confirmDeleteAll.value = false
        viewModelScope.launch { history.deleteAll() }
    }

    private companion object {
        val PERMISSIONS = setOf(Requirement.MICROPHONE, Requirement.ACCESSIBILITY, Requirement.BATTERY, Requirement.OVERLAY)
    }
}
