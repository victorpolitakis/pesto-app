package com.victorpolitakis.pesto.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.data.ProfilesRepository
import com.victorpolitakis.pesto.data.StatsRepository
import com.victorpolitakis.pesto.data.countOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The ready-made profiles, with the default, the apps that get each one and the dictations made with it. */
@HiltViewModel
class ProfilesViewModel @Inject constructor(profiles: ProfilesRepository, stats: StatsRepository) : ViewModel() {
    val state: StateFlow<ProfilesState> = combine(
        profiles.defaultProfile,
        profiles.appProfiles,
        stats.apps(),
        stats.byProfile(),
    ) { default, appProfiles, apps, byProfile ->
        val users = ProfilesRepository.appsByProfile(apps.map { it.packageName }, appProfiles, default)
        val rows = profiles.catalog.map { profile ->
            ProfileRow(profile, profile.id == default, apps = users[profile.id].orEmpty().size, dictations = byProfile.countOf(profile.id))
        }
        ProfilesState(rows, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesState())
}
