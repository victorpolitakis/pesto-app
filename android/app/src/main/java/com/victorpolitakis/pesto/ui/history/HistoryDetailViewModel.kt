package com.victorpolitakis.pesto.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.HistoryRepository
import com.victorpolitakis.pesto.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val history: HistoryRepository,
    apps: AppInfo,
) : ViewModel() {
    private val id = savedState.toRoute<Routes.HistoryDetail>().id

    val state: StateFlow<HistoryDetailState> = flow {
        val detail = history.get(id)
        emit(HistoryDetailState(detail, detail?.let { apps.of(it.packageName) }, loading = false))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryDetailState())

    /**
     * Deletes the dictation. What it returns puts it back, with the same id: the
     * undo of the snackbar (Δ15), which runs after this screen has closed.
     */
    suspend fun delete(): (suspend () -> Unit)? {
        val deleted = history.delete(id) ?: return null
        return { history.restore(deleted) }
    }
}
