package com.peppeosmio.lockate.ui.screens.home_page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.ConnectionSettingsService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomePageViewModel(
    private val connectionSettingsService: ConnectionSettingsService,
    private val anonymousGroupService: AnonymousGroupService,
    private val permissionsService: PermissionsService
) : ViewModel() {

    private val _state = MutableStateFlow(HomePageState())
    val state: StateFlow<HomePageState> = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val connectionSettings = connectionSettingsService.listConnectionSettings()
            if (connectionSettings.isNotEmpty()) {
                _state.update { it.copy(connectionSettings = connectionSettings) }
            } else {
                _state.update { it.copy(shouldRedirectToCredentialsPage = true) }
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

    suspend fun disconnect(): Boolean {
        closeLogoutDialog()
        if (state.value.selectedConnectionSettingsId == null) {
            return false
        }
        _state.update { it.copy(showLoadingOverlay = true) }
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.leaveAllAG(state.value.selectedConnectionSettingsId!!)
            connectionSettingsService.deleteConnectionSettings(state.value.selectedConnectionSettingsId!!)
        }
        if (result.errorInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorInfo = result.errorInfo
                )
            )
            _state.update { it.copy(showLoadingOverlay = false) }
            return false
        }
        _state.update { it.copy(showLoadingOverlay = false) }
        return true
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
        _state.update { it.copy(selectedConnectionSettingsId = connectionSettingsId) }
    }

    fun toggleConnectionsMenu() {
        _state.update { it.copy(isConnectionsMenuOpen = !it.isConnectionsMenuOpen) }
    }

    fun closeConnectionsMenu() {
        _state.update { it.copy(isConnectionsMenuOpen = false) }
    }

    suspend fun onConnectionSelected(connectionSettingsId: Long) {
        if (connectionSettingsId == state.value.selectedConnectionSettingsId) {
            return
        }
        _state.update { it.copy(selectedConnectionSettingsId = connectionSettingsId) }
        connectionSettingsService.saveSelectedConnectionSettingsId(connectionSettingsId)
    }
}
