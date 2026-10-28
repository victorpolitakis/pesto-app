package com.victorpolitakis.pesto.ui.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.StatsRepository
import com.victorpolitakis.pesto.data.countOf
import com.victorpolitakis.pesto.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val profiles: ProfilesRepository,
    stats: StatsRepository,
    apps: AppInfo,
) : ViewModel() {
    private val id = savedState.toRoute<Routes.ProfileDetail>().id

    val state: StateFlow<ProfileDetailState> = combine(
        profiles.defaultProfile,
        profiles.appProfiles,
        stats.apps(),
        stats.byProfile(),
    ) { default, appProfiles, used, byProfile ->
        val users = ProfilesRepository.appsByProfile(used.map { it.packageName }, appProfiles, default)[id].orEmpty()
        ProfileDetailState(
            profile = ProfilesCatalog.find(id),
            isDefault = id == default,
            apps = users.map { ProfileAppUi(apps.of(it.packageName), it.automatic) },
            dictations = byProfile.countOf(id),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileDetailState())

    fun setDefault() {
        viewModelScope.launch { profiles.setDefault(id) }
    }
}
