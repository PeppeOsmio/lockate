package com.peppeosmio.lockate.ui.screens.connection_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.ServerUrl
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.ServerAlreadyAddedException
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.version.apiCompatibility
import com.peppeosmio.lockate.service.version.incompatibilityMessage
import com.peppeosmio.lockate.service.version.serverVersionLabel
import com.peppeosmio.lockate.ui.routes.ServerScreenMode
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionSettingsViewModel(
    private val connectionService: ConnectionService,
    private val anonymousGroupService: AnonymousGroupService,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionSettingsState())
    val state: StateFlow<ConnectionSettingsState> = _state
    private val _snackbarEvents = Channel<SnackbarErrorMessage>(Channel.BUFFERED)
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    // after adding a server, or picking the one already saved, the map opens on it; the message
    // says why when it isn't a new server
    private val _navigateHomeEvents = Channel<String?>(Channel.BUFFERED)
    val navigateHomeEvents = _navigateHomeEvents.receiveAsFlow()

    // after saving or removing the server being edited
    private val _navigateBackEvents = Channel<Unit>(Channel.BUFFERED)
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()

    private var connectionId: Long? = null
    private var loaded = false

    fun load(mode: ServerScreenMode, connectionId: Long?) {
        if (loaded) return
        loaded = true
        this.connectionId = connectionId
        _state.update { it.copy(mode = mode) }
        if (mode != ServerScreenMode.Edit || connectionId == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                val connection = connectionService.getConnectionSettingsById(connectionId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        savedName = connection.name,
                        name = connection.name,
                        isNameEdited = true,
                        url = connection.url,
                        apiKey = connection.apiKey ?: "",
                        groups =
                            anonymousGroupService.listLocalAnonymousGroups(connectionId).sortedBy {
                                group ->
                                group.name.lowercase()
                            },
                    )
                }
                loadVersionLabel(connection)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage("Can't load the server", ErrorInfo.fromException(e))
                )
            }
        }
    }

    private suspend fun loadVersionLabel(connection: Connection) {
        val label =
            try {
                serverVersionLabel(
                    connectionService.getServerVersion(connection.url, connection.apiKey)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
        _state.update { it.copy(versionLabel = label) }
    }

    fun onNameChanged(name: String) {
        _state.update { it.copy(name = name, isNameEdited = true) }
    }

    fun onUrlChanged(url: String) {
        _state.update {
            it.copy(
                url = url,
                name = if (it.isNameEdited) it.name else ServerUrl.host(url).orEmpty(),
            )
        }
    }

    fun onApiKeyChanged(apiKey: String) {
        _state.update { it.copy(apiKey = apiKey) }
    }

    /** Checks the server and the API key, then adds the server or saves the one being edited */
    fun connect() {
        val current = state.value
        if (current.isWorking) return
        if (current.url.isBlank()) {
            _snackbarEvents.trySend(SnackbarErrorMessage(text = "Enter the server's address"))
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val connection = checkServer(current)
            if (connection == null) {
                _state.update { it.copy(isWorking = false) }
                return@launch
            }
            try {
                if (current.mode == ServerScreenMode.Edit) {
                    connectionService.updateConnection(connection)
                    _navigateBackEvents.trySend(Unit)
                } else {
                    val saved = connectionService.saveConnectionSettings(connection)
                    connectionService.saveSelectedConnectionSettingsId(saved.id!!)
                    _navigateHomeEvents.trySend(null)
                }
            } catch (e: ServerAlreadyAddedException) {
                if (current.mode == ServerScreenMode.Edit) {
                    _snackbarEvents.trySend(
                        SnackbarErrorMessage(text = "Another server has this address")
                    )
                } else {
                    connectionService.saveSelectedConnectionSettingsId(e.connectionId)
                    _navigateHomeEvents.trySend("Already added")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage("Can't save the server", ErrorInfo.fromException(e))
                )
            }
            _state.update { it.copy(isWorking = false) }
        }
    }

    /**
     * The server to save, once it answers with our API and accepts the API key; null after telling
     * the user why not.
     */
    private suspend fun checkServer(current: ConnectionSettingsState): Connection? {
        val url = ServerUrl.normalize(current.url)
        // First, as a server speaking another API may not have the other endpoints below
        val compatibility = ErrorHandler.runAndHandleException {
            apiCompatibility(connectionService.getServerVersion(url, null)?.apis)
        }
        if (compatibility.errorInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage("Can't reach the server", errorInfo = compatibility.errorInfo)
            )
            return null
        }
        incompatibilityMessage(compatibility.value!!)?.let { message ->
            _snackbarEvents.trySend(SnackbarErrorMessage(text = message))
            return null
        }
        val required = ErrorHandler.runAndHandleException {
            connectionService.checkRequireApiKey(url)
        }
        if (required.errorInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage("Can't reach the server", errorInfo = required.errorInfo)
            )
            return null
        }
        val apiKey = current.apiKey.trim().ifEmpty { null }
        if (required.value!! && apiKey == null) {
            _snackbarEvents.trySend(SnackbarErrorMessage(text = "This server needs an API key"))
            return null
        }
        val connection =
            Connection(
                id = connectionId.takeIf { current.mode == ServerScreenMode.Edit },
                name = current.name.trim().ifEmpty { ServerUrl.host(url) ?: url },
                url = url,
                apiKey = apiKey,
            )
        try {
            connectionService.testConnectionSettings(connection)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidApiKeyException) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage("The API key isn't valid", ErrorInfo.fromException(e))
            )
            return null
        } catch (e: Exception) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage("Can't reach the server", ErrorInfo.fromException(e))
            )
            return null
        }
        return connection
    }

    fun askToRemove() {
        _state.update { it.copy(showRemoveDialog = true) }
    }

    fun cancelRemove() {
        _state.update { it.copy(showRemoveDialog = false) }
    }

    /** Leaves the server's groups (or drops them, when it can't be reached) and forgets it */
    fun remove() {
        val id = connectionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(showRemoveDialog = false, isWorking = true) }
            try {
                try {
                    anonymousGroupService.leaveAllAG(connectionSettingsId = id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    anonymousGroupService.deleteAllAG(connectionSettingsId = id)
                }
                connectionService.deleteConnection(id)
                _navigateBackEvents.trySend(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isWorking = false) }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage("Can't remove the server", ErrorInfo.fromException(e))
                )
            }
        }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogError = null) }
    }

    fun showErrorDialog(error: SnackbarErrorMessage) {
        _state.update { it.copy(dialogError = error) }
    }
}
