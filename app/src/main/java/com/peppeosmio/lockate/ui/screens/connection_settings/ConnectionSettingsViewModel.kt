package com.peppeosmio.lockate.ui.screens.connection_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.ConnectionSettings
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.service.ConnectionSettingsService
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
            if (result.errorInfo != null) {
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
        if (state.value.requireApiKey && state.value.apiKey.isBlank()) {
            _snackbarEvents.trySend(SnackbarErrorMessage(text = "Please enter api key"))
            return
        }
        _state.update { it.copy(showLoadingOverlay = true) }
        if (!state.value.requireApiKey) {
            val result = ErrorHandler.runAndHandleException {
                connectionSettingsService.checkRequireApiKey(state.value.url)
            }
            if (result.errorInfo != null) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        "Connection error", errorInfo = result.errorInfo
                    )
                )
                result.errorInfo.exception?.printStackTrace()
                _state.update { it.copy(showLoadingOverlay = false) }
                return
            }
            if (result.value!!) {
                _snackbarEvents.trySend(SnackbarErrorMessage(text = "Please enter api key"))
                _state.update { it.copy(showLoadingOverlay = false, requireApiKey = true) }
                return
            } else {
                _state.update { it.copy(showLoadingOverlay = false) }
            }
        }
        val connectionSettings = ConnectionSettings(
            id = null,
            url = _state.value.url,
            apiKey = if(state.value.requireApiKey) state.value.apiKey else null ,
            username = null,
            authToken = null
        )
        try {
            connectionSettingsService.testConnectionSettings(connectionSettings)
        } catch (e: InvalidApiKeyException) {
            val errorInfo = ErrorInfo.fromException(e)
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Invalid api key", errorInfo = errorInfo
                )
            )
            _state.update { it.copy(showLoadingOverlay = false) }
            return
        }
        catch (e: Exception) {
            val errorInfo = ErrorInfo.fromException(e)
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorInfo = errorInfo
                )
            )
            _state.update { it.copy(showLoadingOverlay = false) }
            return
        }
        val result = ErrorHandler.runAndHandleException {
            connectionSettingsService.saveConnectionSettings(
                connectionSettings
            )
        }
        if (result.errorInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorInfo = result.errorInfo
                )
            )
            _state.update { it.copy(showLoadingOverlay = false) }
            return
        }
        _state.update { it.copy(showLoadingOverlay = false) }
        _navigateToHome.emit(result.value!!.id!!)

    }

    fun hideErrorDialog() {
        _state.update { it.copy(errorInfo = null) }
    }

    fun showErrorDialog(errorInfo: ErrorInfo) {
        _state.update { it.copy(errorInfo = errorInfo) }
    }
}
