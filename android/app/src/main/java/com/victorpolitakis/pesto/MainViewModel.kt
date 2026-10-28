package com.victorpolitakis.pesto

import androidx.lifecycle.ViewModel
import com.victorpolitakis.pesto.permissions.PermissionFlow
import com.victorpolitakis.pesto.permissions.Requirement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the status page shows. Temporary page: Home replaces it in phase 3. */
data class StatusState(
    val granted: Map<Requirement, Boolean> = emptyMap(),
    val backendUrl: String = "",
    val dictations: Int? = null,
)

/** Keeps the permission flow across rotations, so "asked in this visit" is not forgotten. */
class MainViewModel : ViewModel() {
    private var flow: PermissionFlow? = null
    private val _status = MutableStateFlow(StatusState())
    val status: StateFlow<StatusState> = _status.asStateFlow()

    fun flow(order: List<Requirement>): PermissionFlow = flow ?: PermissionFlow(order).also { flow = it }

    fun update(transform: (StatusState) -> StatusState) {
        _status.value = transform(_status.value)
    }
}
