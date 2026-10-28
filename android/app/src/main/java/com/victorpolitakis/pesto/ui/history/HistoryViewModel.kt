package com.victorpolitakis.pesto.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.ui.components.toRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** The History, a page more at the end of the list. A new dictation shows up at once (Room flow). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(history: HistoryRepository, apps: AppInfo) : ViewModel() {
    private val limit = MutableStateFlow(HistoryRepository.PAGE)

    val state: StateFlow<HistoryState> = combine(
        limit.flatMapLatest { history.list(it) }.map { items -> items.map { it.toRow(apps) } },
        history.total,
    ) { rows, total -> HistoryState(rows, total, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun loadMore() {
        limit.update { it + HistoryRepository.PAGE }
    }
}
