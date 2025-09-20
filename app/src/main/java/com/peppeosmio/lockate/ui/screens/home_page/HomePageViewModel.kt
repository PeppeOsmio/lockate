package com.peppeosmio.lockate.ui.screens.home_page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.ConnectionSettingsService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.utils.ErrorDialogInfo
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
            if(connectionSettings.isNotEmpty()) {
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

    suspend fun disconnect() {
        closeLogoutDialog()
        if(state.value.connectionSettingsId == null) {
            return
        }
        _state.update { it.copy(showLoadingOverlay = true) }
        anonymousGroupService.leaveAllAG(state.value.connectionSettingsId!!)
        connectionSettingsService.deleteConnectionSettings(state.value.connectionSettingsId!!)
        _state.update { it.copy(showLoadingOverlay = false) }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogErrorDialogInfo = null) }
    }

    fun showErrorDialog(errorDialogInfo: ErrorDialogInfo) {
        _state.update { it.copy(dialogErrorDialogInfo = errorDialogInfo) }
    }

    fun showSnackbar(snackbarErrorMessage: SnackbarErrorMessage) {
        _snackbarEvents.trySend(snackbarErrorMessage)
    }

    fun setConnectionSettingsId(connectionSettingsId: Long) {
        _state.update { it.copy(connectionSettingsId = connectionSettingsId) }
    }
}
