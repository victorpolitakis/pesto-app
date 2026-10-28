package com.victorpolitakis.pesto.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.data.StatsRepository
import com.victorpolitakis.pesto.ui.components.toRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The numbers of Home (Δ21), its top 3 apps and its last 5 dictations (Δ22), all from the History. */
@HiltViewModel
class HomeViewModel @Inject constructor(stats: StatsRepository, history: HistoryRepository, apps: AppInfo) : ViewModel() {
    val state: StateFlow<HomeState> = combine(stats.totals(), stats.apps(), history.list(RECENT)) { totals, used, recent ->
        HomeState(
            totals = totals,
            topApps = used.take(TOP_APPS).map { apps.of(it.packageName) },
            recent = recent.map { it.toRow(apps) },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private companion object {
        const val RECENT = 5
        const val TOP_APPS = 3
    }
}
