package com.peppeosmio.lockate.ui.screens.manage_connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ManageConnectionsViewModel(
    private val connectionService: ConnectionService,
    private val anonymousGroupService: AnonymousGroupService
) : ViewModel() {

    private val _state = MutableStateFlow(ManageConnectionsState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _navigateBackEvents = Channel<Unit>()
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()

    fun getInitialData() {
        viewModelScope.launch {
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                val connections = connectionService.listConnectionSettings()
                _state.update { it.copy(showLoadingOverlay = false, connections = connections) }
            } catch (e: Exception) {
                _state.update { it.copy(showLoadingOverlay = false) }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't get connections", errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
        }
    }

    fun selectForDelete(connectionId: Long) {
        _state.update { it.copy(selectedConnectionIdForDelete = connectionId) }
    }

    fun unselectForDelete() {
        _state.update { it.copy(selectedConnectionIdForDelete = null) }
    }

    fun openDeleteConfirmDialog() {
        _state.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun closeDeleteConfirmDialog() {
        _state.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun deleteConnection() {
        viewModelScope.launch {
            val connectionId = state.value.selectedConnectionIdForDelete ?: return@launch
            _state.update { it.copy(showLoadingOverlay = true, showDeleteConfirmDialog = false) }
            try {
                try {
                    anonymousGroupService.leaveAllAG(connectionSettingsId = connectionId)
                } catch (e: Exception) {
                    anonymousGroupService.deleteAllAG(connectionSettingsId = connectionId)
                }
                connectionService.deleteConnection(connectionId)
                val newConnections = state.value.connections!!.filterNot { it.id == connectionId }
                _state.update {
                    it.copy(
                        showLoadingOverlay = false,
                        connections = newConnections,
                        selectedConnectionIdForDelete = null
                    )
                }
                if (newConnections.isEmpty()) {
                    _navigateBackEvents.trySend(Unit)
                }
            } catch (e: Exception) {
                _state.update { it.copy(showLoadingOverlay = false) }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't delete connection", errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
        }
    }

    fun showErrorDialog(error: SnackbarErrorMessage) {
        _state.update { it.copy(dialogError = error) }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogError = null) }
    }
}
