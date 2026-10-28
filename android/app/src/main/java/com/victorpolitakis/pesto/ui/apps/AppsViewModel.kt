package com.victorpolitakis.pesto.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The apps of the History and the profile of each; the dialog of one app picks its profile (C9). */
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val profiles: ProfilesRepository,
    stats: StatsRepository,
    apps: AppInfo,
) : ViewModel() {
    private val choosing = MutableStateFlow<String?>(null)

    val state: StateFlow<AppsState> = combine(
        stats.apps(),
        profiles.appProfiles,
        profiles.defaultProfile,
        choosing,
    ) { used, own, default, open ->
        AppsState(
            rows = used.map { AppRow(apps.of(it.packageName), it.dictations, it.lastUsed, own[it.packageName]) },
            defaultProfile = default,
            choosing = open,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsState())

    fun choose(packageName: String) {
        choosing.value = packageName
    }

    fun dismiss() {
        choosing.value = null
    }

    /** [profileId] null: automatic, the app follows the default. */
    fun setProfile(profileId: String?) {
        val packageName = choosing.value ?: return
        choosing.value = null
        viewModelScope.launch { profiles.setAppProfile(packageName, profileId) }
    }
}
