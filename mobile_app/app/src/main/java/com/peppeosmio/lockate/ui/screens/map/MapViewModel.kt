package com.peppeosmio.lockate.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.service.ConnectionEvent
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupEvent
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val connectionService: ConnectionService,
    private val anonymousGroupService: AnonymousGroupService,
    private val permissionsService: PermissionsService
) : ViewModel() {

    private val _state = MutableStateFlow(MapState())
    val state: StateFlow<MapState> = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>(Channel.BUFFERED)
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _navigateToConnectionSettingsEvents = Channel<ConnectionSettingsRoute>(Channel.BUFFERED)
    val navigateToConnectionSettingsEvents = _navigateToConnectionSettingsEvents.receiveAsFlow()
    private var loadGroupsJob: Job? = null
    private var verifyJob: Job? = null
    private val noConnectionsRoute =
        ConnectionSettingsRoute(initialConnectionSettingsId = null, showBackButton = false)

    init {
        viewModelScope.launch { loadConnections() }
        viewModelScope.launch { collectConnectionEvents() }
        viewModelScope.launch { collectAGEvents() }
    }

    private suspend fun loadConnections() {
        val connections = connectionService.listConnectionSettings()
        if (connections.isEmpty()) {
            _navigateToConnectionSettingsEvents.send(noConnectionsRoute)
            return
        }
        val selectedConnectionId = try {
            connectionService.getSelectedConnectionSettings().id
        } catch (e: ConnectionSettingsNotFoundException) {
            _snackbarEvents.trySend(SnackbarErrorMessage(text = "No connection settings found!"))
            return
        }
        _state.update {
            it.copy(
                connections = connections.associateBy { connection -> connection.id!! },
                selectedConnectionId = selectedConnectionId
            )
        }
        selectedConnectionId?.let { loadGroups(it) }
    }

    private suspend fun collectConnectionEvents() {
        connectionService.events.collect { event ->
            when (event) {
                is ConnectionEvent.ConnectionDeletedEvent -> {
                    val currentState = state.value
                    val connections = currentState.connections
                    if (connections == null || event.connectionId !in connections) {
                        return@collect
                    }
                    val newConnections = connections - event.connectionId
                    _state.update {
                        it.copy(
                            connections = newConnections,
                            unreachableConnectionIds = it.unreachableConnectionIds - event.connectionId
                        )
                    }
                    if (newConnections.isEmpty()) {
                        _navigateToConnectionSettingsEvents.send(noConnectionsRoute)
                    } else if (event.connectionId == currentState.selectedConnectionId) {
                        selectConnection(newConnections.keys.first(), force = true)
                    }
                }
            }
        }
    }

    private suspend fun collectAGEvents() {
        anonymousGroupService.events.collect { event ->
            when (event) {
                is AnonymousGroupEvent.NewAnonymousGroupEvent -> {
                    if (event.connectionId != state.value.selectedConnectionId || state.value.groups == null) {
                        return@collect
                    }
                    val created = anonymousGroupService.getAGByInternalId(event.anonymousGroupInternalId)
                    _state.update { it.copy(groups = (it.groups ?: emptyList()) + created) }
                    selectGroup(created.internalId)
                }

                is AnonymousGroupEvent.DeleteAnonymousGroupEvent -> {
                    val groups = state.value.groups ?: return@collect
                    val remaining = groups.filterNot { it.id == event.anonymousGroupId }
                    if (remaining.size == groups.size) {
                        return@collect
                    }
                    _state.update { it.copy(groups = remaining) }
                    if (state.value.selectedGroup == null) {
                        selectGroup(pickInitialGroup(remaining, lastInternalId = null)?.internalId)
                    }
                }

                is AnonymousGroupEvent.RemoteAGDoesntExistEvent ->
                    updateGroup(event.anonymousGroupId) { it.copy(existsRemote = false) }

                is AnonymousGroupEvent.RemoteAGExistsEvent ->
                    updateGroup(event.anonymousGroupId) { it.copy(existsRemote = true) }

                is AnonymousGroupEvent.RemovedFromAnonymousGroupEvent ->
                    updateGroup(event.anonymousGroupId) { it.copy(isMember = false) }

                is AnonymousGroupEvent.ReaddedToAnonymousGroupEvent ->
                    updateGroup(event.anonymousGroupId) { it.copy(isMember = true) }

                else -> Unit
            }
        }
    }

    private fun updateGroup(
        anonymousGroupId: String,
        transform: (AnonymousGroup) -> AnonymousGroup
    ) {
        _state.update { current ->
            current.copy(groups = current.groups?.map { if (it.id == anonymousGroupId) transform(it) else it })
        }
    }

    private fun loadGroups(connectionId: Long) {
        loadGroupsJob?.cancel()
        verifyJob?.cancel()
        loadGroupsJob = viewModelScope.launch {
            _state.update { it.copy(isServerUnreachable = false) }
            val groups = try {
                anonymousGroupService.listLocalAnonymousGroups(connectionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't get local anonymous groups", errorInfo = ErrorInfo.fromException(e)
                    )
                )
                emptyList()
            }
            val initial = pickInitialGroup(groups, connectionService.getLastAGInternalId(connectionId))
            _state.update { it.copy(groups = groups, selectedGroupInternalId = initial?.internalId) }
            verifyGroups(connectionId)
        }
    }

    private suspend fun verifyGroups(connectionId: Long) {
        for (group in state.value.groups ?: return) {
            try {
                anonymousGroupService.verifyMemberAuth(
                    connectionSettingsId = connectionId, anonymousGroupInternalId = group.internalId
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when (e) {
                    is UnauthorizedException, is RemoteAGNotFoundException -> Unit
                    else -> {
                        val errorInfo = ErrorInfo.fromException(e)
                        if (errorInfo.isConnectionError) {
                            _state.update { it.copy(isServerUnreachable = true) }
                        } else {
                            _snackbarEvents.trySend(
                                SnackbarErrorMessage(text = "Connection error", errorInfo = errorInfo)
                            )
                        }
                        return
                    }
                }
            }
        }
    }

    fun retryServer() {
        val connectionId = state.value.selectedConnectionId ?: return
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            _state.update { it.copy(isServerUnreachable = false) }
            verifyGroups(connectionId)
        }
    }

    fun selectConnection(connectionId: Long, force: Boolean = false) {
        if (!force && connectionId == state.value.selectedConnectionId) {
            return
        }
        _state.update {
            it.copy(
                selectedConnectionId = connectionId,
                groups = null,
                selectedGroupInternalId = null,
                isGroupPickerOpen = false,
                groupQuery = ""
            )
        }
        viewModelScope.launch { connectionService.saveSelectedConnectionSettingsId(connectionId) }
        loadGroups(connectionId)
    }

    fun selectGroup(anonymousGroupInternalId: Long?) {
        val connectionId = state.value.selectedConnectionId ?: return
        _state.update {
            it.copy(
                selectedGroupInternalId = anonymousGroupInternalId,
                isGroupPickerOpen = false,
                groupQuery = ""
            )
        }
        if (anonymousGroupInternalId != null) {
            viewModelScope.launch {
                connectionService.saveLastAGInternalId(connectionId, anonymousGroupInternalId)
            }
        }
    }

    fun removeLocalGroup() {
        val group = state.value.selectedGroup ?: return
        viewModelScope.launch {
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                anonymousGroupService.deleteLocalAnonymousGroup(group)
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't remove anonymous group", errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
            _state.update { it.copy(showLoadingOverlay = false) }
        }
    }

    fun refreshServersReachability() {
        val connections = state.value.connections ?: return
        _state.update { it.copy(unreachableConnectionIds = emptySet()) }
        connections.values.forEach { connection ->
            viewModelScope.launch {
                try {
                    connectionService.isApiAvailable(connection.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(unreachableConnectionIds = it.unreachableConnectionIds + connection.id!!) }
                }
            }
        }
    }

    fun openGroupPicker() {
        _state.update { it.copy(isGroupPickerOpen = true) }
    }

    fun closeGroupPicker() {
        _state.update { it.copy(isGroupPickerOpen = false, groupQuery = "") }
    }

    fun setGroupQuery(query: String) {
        _state.update { it.copy(groupQuery = query) }
    }

    fun checkPermissionGranted(permission: String): Boolean =
        permissionsService.isPermissionGranted(permission)

    fun showSnackbar(message: SnackbarErrorMessage) {
        _snackbarEvents.trySend(message)
    }

    fun showErrorDialog(error: SnackbarErrorMessage) {
        _state.update { it.copy(dialogError = error) }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogError = null) }
    }
}
