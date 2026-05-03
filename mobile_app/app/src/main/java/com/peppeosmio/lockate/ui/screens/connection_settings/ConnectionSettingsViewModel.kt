package com.peppeosmio.lockate.ui.screens.connection_settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionSettingsViewModel(
    private val connectionService: ConnectionService
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionSettingsState())
    val state: StateFlow<ConnectionSettingsState> = _state
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    private val _navigateHomeEvents = Channel<Unit>()
    val navigateHomeEvents = _navigateHomeEvents.receiveAsFlow()

    fun getInitialData(initialConnectionSettingsId: Long?) {
        Log.d("ConnectionSettingsViewModel", "initialConnectionSettingsId: $initialConnectionSettingsId")
        if (initialConnectionSettingsId == null) {
            _state.update { it.copy(showLoadingOverlay = false) }
            return
        }
        viewModelScope.launch {
            try {
                val connectionSettings = connectionService.getConnectionSettingsById(initialConnectionSettingsId)
                _state.update {
                    it.copy(
                        showLoadingOverlay = false,
                        apiKey = connectionSettings.apiKey ?: "",
                        url = connectionSettings.url
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(showLoadingOverlay = false) }
            }
        }
    }

    fun onUrlChanged(url: String) {
        _state.update { it.copy(url = url) }
    }

    fun onApiKeyChanged(apiKey: String) {
        _state.update { it.copy(apiKey = apiKey) }
    }

    fun onConnectClicked(initialConnectionId: Long?) {
        viewModelScope.launch {
            if (state.value.url.isBlank()) {
                _snackbarEvents.trySend(SnackbarErrorMessage(text = "Please enter url"))
                return@launch
            }
            if (state.value.requireApiKey && state.value.apiKey.isBlank()) {
                _snackbarEvents.trySend(SnackbarErrorMessage(text = "Please enter api key"))
                return@launch
            }
            _state.update { it.copy(showLoadingOverlay = true) }
            if (!state.value.requireApiKey) {
                val result = ErrorHandler.runAndHandleException {
                    connectionService.checkRequireApiKey(state.value.url)
                }
                if (result.errorInfo != null) {
                    _snackbarEvents.trySend(
                        SnackbarErrorMessage(
                            "Connection error", errorInfo = result.errorInfo
                        )
                    )
                    result.errorInfo.exception?.printStackTrace()
                    _state.update { it.copy(showLoadingOverlay = false) }
                    return@launch
                }
                if (result.value!!) {
                    _snackbarEvents.trySend(SnackbarErrorMessage(text = "Please enter api key"))
                    _state.update { it.copy(showLoadingOverlay = false, requireApiKey = true) }
                    return@launch
                } else {
                    _state.update { it.copy(showLoadingOverlay = false) }
                }
            }
            val connection = Connection(
                id = initialConnectionId,
                url = _state.value.url,
                apiKey = if(state.value.requireApiKey) state.value.apiKey else null ,
                username = null,
                authToken = null
            )
            try {
                connectionService.testConnectionSettings(connection)
            } catch (e: InvalidApiKeyException) {
                val errorInfo = ErrorInfo.fromException(e)
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Invalid api key", errorInfo = errorInfo
                    )
                )
                _state.update { it.copy(showLoadingOverlay = false) }
                return@launch
            }
            catch (e: Exception) {
                val errorInfo = ErrorInfo.fromException(e)
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Connection error", errorInfo = errorInfo
                    )
                )
                _state.update { it.copy(showLoadingOverlay = false) }
                return@launch
            }
            try {
               if(initialConnectionId != null) {
                   connectionService.updateConnection(
                       connection
                   )
               } else {
                   connectionService.saveConnectionSettings(
                       connection
                   )
               }
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Connection error", errorInfo = ErrorInfo.fromException(e)
                    )
                )
                _state.update { it.copy(showLoadingOverlay = false) }
                return@launch
            }
            _state.update { it.copy(showLoadingOverlay = false) }
            _navigateHomeEvents.trySend(Unit)
        }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(errorInfo = null) }
    }

    fun showErrorDialog(errorInfo: ErrorInfo) {
        _state.update { it.copy(errorInfo = errorInfo) }
    }
}
