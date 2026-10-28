package com.victorpolitakis.pesto.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.ui.components.toRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Searches as the user types, a moment after the last key. The field itself lives in the screen. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(history: HistoryRepository, apps: AppInfo) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<SearchState> = query
        .debounce(DEBOUNCE_MS)
        .flatMapLatest { q -> history.search(q).map { results -> q to results } }
        .map { (q, results) -> SearchState(q, results.map { SearchHit(it.item.toRow(apps), it.snippet) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun search(text: String) {
        query.value = text
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
    }
}
