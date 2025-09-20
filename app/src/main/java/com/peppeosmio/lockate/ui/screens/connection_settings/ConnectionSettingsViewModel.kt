package com.peppeosmio.lockate.ui.screens.connection_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.ConnectionSettings
import com.peppeosmio.lockate.service.ConnectionSettingsService
import com.peppeosmio.lockate.utils.ErrorDialogInfo
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionSettingsViewModel(
    private val connectionSettingsService: ConnectionSettingsService
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionSettingsState())
    val state: StateFlow<ConnectionSettingsState> = _state
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    private val _navigateToHome = MutableSharedFlow<Long>()
    val navigateToHome = _navigateToHome.asSharedFlow()

    fun getInitialData(initialConnectionSettingsId: Long?) {
        if (initialConnectionSettingsId == null) {
            _state.update { it.copy(showLoadingOverlay = false) }
            return
        }
        viewModelScope.launch {
            val result = ErrorHandler.runAndHandleException {
                connectionSettingsService.getConnectionSettingsById(initialConnectionSettingsId)
            }
            if (result.errorDialogInfo != null) {
                _state.update { it.copy(showLoadingOverlay = false) }
            } else {
                _state.update {
                    it.copy(
                        showLoadingOverlay = false,
                        apiKey = result.value!!.apiKey ?: "",
                        url = result.value.url
                    )
                }
            }
        }
    }

    fun onUrlChanged(url: String) {
        _state.update { it.copy(url = url) }
    }

    fun onApiKeyChanged(apiKey: String) {
        _state.update { it.copy(apiKey = apiKey) }
    }

    suspend fun onConnectClicked() {
        if (state.value.url.isBlank()) {
            _snackbarEvents.trySend(SnackbarErrorMessage(text = "Please enter url"))
            return
        }
        if(state.value.requireApiKey && state.value.apiKey.isBlank()) {
            _snackbarEvents.trySend(SnackbarErrorMessage(text = "Please enter api key"))
            return
        }

        _state.update { it.copy(showLoadingOverlay = true) }
        val result = ErrorHandler.runAndHandleException {
            val connectionSettings = connectionSettingsService.saveConnectionSettings(
                ConnectionSettings(
                    id = null,
                    url = _state.value.url,
                    apiKey = _state.value.apiKey,
                    username = null,
                    authToken = null
                )
            )
            _navigateToHome.emit(connectionSettings.id!!)
        }
        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorDialogInfo = result.errorDialogInfo
                )
            )
        }
        _state.update { it.copy(showLoadingOverlay = false) }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(errorDialogInfo = null) }
    }

    fun showErrorDialog(errorDialogInfo: ErrorDialogInfo) {
        _state.update { it.copy(errorDialogInfo = errorDialogInfo) }
    }
}
