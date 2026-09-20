package com.peppeosmio.lockate.ui.screens.home_page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.ConnectionEvent
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomePageViewModel(
    private val connectionService: ConnectionService,
    private val anonymousGroupService: AnonymousGroupService,
    private val permissionsService: PermissionsService
) : ViewModel() {

    private val _state = MutableStateFlow(HomePageState())
    val state: StateFlow<HomePageState> = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _navigateToConnectionSettingsEvents = Channel<ConnectionSettingsRoute>()
    val navigateToConnectionSettingsEvents = _navigateToConnectionSettingsEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val connectionSettings = connectionService.listConnectionSettings()
            if (connectionSettings.isNotEmpty()) {
                _state.update { it.copy(connections = connectionSettings.associateBy { connectionSettings -> connectionSettings.id!! }) }
            } else {
                _navigateToConnectionSettingsEvents.send(
                    ConnectionSettingsRoute(
                        initialConnectionSettingsId = null, showBackButton = false
                    )
                )
            }
            try {
                val selectedConnectionSettings = connectionService.getSelectedConnectionSettings()
                _state.update { it.copy(selectedConnectionId = selectedConnectionSettings.id) }
            } catch (e: ConnectionSettingsNotFoundException) {
                _snackbarEvents.trySend(SnackbarErrorMessage(text = "No connection settings found!"))
            }
        }
        viewModelScope.launch {
            connectionService.events.collect { event ->
                when (event) {
                    is ConnectionEvent.ConnectionDeletedEvent -> {
                        val currentState = state.value
                        if (currentState.connections == null || event.connectionId !in currentState.connections) {
                            return@collect
                        }
                        val newConnections = currentState.connections - event.connectionId
                        val newSelectedConnectionId = if (event.connectionId == currentState.selectedConnectionId) {
                            val newId = newConnections.keys.firstOrNull()
                            if (newId != null) {
                                connectionService.saveSelectedConnectionSettingsId(newId)
                            }
                            newId
                        } else {
                            currentState.selectedConnectionId
                        }
                        _state.update {
                            it.copy(
                                connections = newConnections, selectedConnectionId = newSelectedConnectionId
                            )
                        }
                        if (newConnections.isEmpty()) {
                            _navigateToConnectionSettingsEvents.send(
                                ConnectionSettingsRoute(
                                    initialConnectionSettingsId = null, showBackButton = false
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun selectPage(page: Int) {
        _state.update { it.copy(pageSelected = page) }
    }

    fun setSearchText(searchText: String) {
        _state.update { it.copy(searchText = searchText) }
    }

    fun onTapSearchBar() {
        if (!_state.value.isSearchBarOpen) {
            _state.update { it.copy(isSearchBarOpen = true) }
        }
    }

    fun closeSearchBar() {
        _state.update { it.copy(isSearchBarOpen = false) }
    }

    fun registerOnTapFab(onTapFab: () -> Unit) {
        _state.update { it.copy(registeredOnTapFab = onTapFab) }
    }

    fun unregisterOnTapFab() {
        _state.update { it.copy(registeredOnTapFab = null) }
    }

    fun registerOnSearch(onSearch: (query: String) -> Unit) {
        _state.update { it.copy(registeredOnSearch = onSearch) }
    }

    fun unregisterOnSearch() {
        _state.update { it.copy(registeredOnSearch = null) }
    }

    fun openLogoutDialog() {
        _state.update { it.copy(showLogoutDialog = true) }
    }

    fun closeLogoutDialog() {
        _state.update { it.copy(showLogoutDialog = false) }
    }

    fun checkPermissionGranted(permission: String): Boolean {
        return permissionsService.isPermissionGranted(permission)
    }

    fun disconnect() = viewModelScope.launch {
        closeLogoutDialog()
        if (state.value.selectedConnectionId == null) {
            return@launch
        }
        _state.update { it.copy(showLoadingOverlay = true) }
        try {
            try {
                anonymousGroupService.leaveAllAG(state.value.selectedConnectionId!!)
            } catch (e: Exception) {
                anonymousGroupService.deleteAllAG(state.value.selectedConnectionId!!)
            }
            connectionService.deleteConnection(state.value.selectedConnectionId!!)
            _state.update { it.copy(showLoadingOverlay = false) }
        } catch (e: Exception) {
            _state.update { it.copy(showLoadingOverlay = false) }
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorInfo = ErrorInfo.fromException(e)
                )
            )
        }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogErrorInfo = null) }
    }

    fun showErrorDialog(errorInfo: ErrorInfo) {
        _state.update { it.copy(dialogErrorInfo = errorInfo) }
    }

    fun showSnackbar(snackbarErrorMessage: SnackbarErrorMessage) {
        _snackbarEvents.trySend(snackbarErrorMessage)
    }

    fun setSelectedConnectionSettingsId(connectionSettingsId: Long) {
        _state.update { it.copy(selectedConnectionId = connectionSettingsId) }
    }

    fun toggleConnectionsMenu() {
        _state.update { it.copy(isConnectionsDialogOpen = !it.isConnectionsDialogOpen) }
    }

    fun closeConnectionsMenu() {
        _state.update { it.copy(isConnectionsDialogOpen = false) }
    }

    suspend fun onConnectionSelected(connectionSettingsId: Long) {
        if (connectionSettingsId == state.value.selectedConnectionId) {
            return
        }
        _state.update { it.copy(selectedConnectionId = connectionSettingsId) }
        connectionService.saveSelectedConnectionSettingsId(connectionSettingsId)
    }
}
