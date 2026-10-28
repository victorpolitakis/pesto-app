package com.victorpolitakis.pesto.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Every number of the History (C10): totals, the last 30 days, per app, per profile, and where something fails. */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(stats: StatsRepository, apps: AppInfo) : ViewModel() {
    private val reliability = combine(stats.byFallbackReason(), stats.byEndReason(), stats.byInsertMethod()) { fallback, ends, methods ->
        Triple(fallback, ends, methods)
    }

    val state: StateFlow<AnalyticsState> = combine(
        stats.totals(),
        stats.perDay(AnalyticsState.MONTH),
        stats.apps(),
        stats.byProfile(),
        reliability,
    ) { totals, days, used, byProfile, (fallback, ends, methods) ->
        AnalyticsState(
            totals = totals,
            days = days,
            apps = used.map { AppCount(apps.of(it.packageName), it.dictations) },
            profiles = byProfile,
            fallbackReasons = fallback,
            endReasons = ends,
            insertMethods = methods,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())
}
