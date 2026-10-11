package com.peppeosmio.lockate.ui.screens.home_page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.service.ConnectionEvent
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
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

    fun checkPermissionGranted(permission: String): Boolean {
        return permissionsService.isPermissionGranted(permission)
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogError = null) }
    }

    fun showErrorDialog(error: SnackbarErrorMessage) {
        _state.update { it.copy(dialogError = error) }
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
