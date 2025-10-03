package com.peppeosmio.lockate.ui.screens.create_anonymous_group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateAnonymousGroupViewModel(
    private val anonymousGroupService: AnonymousGroupService
) : ViewModel() {
    private val _state = MutableStateFlow(CreateAnonymousGroupState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _navigateBackEvents = Channel<Unit>()
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()

    fun setGroupNameText(text: String) {
        _state.update { it.copy(groupNameText = text) }
    }

    fun setMemberPasswordText(text: String) {
        _state.update { it.copy(memberPasswordText = text) }
    }

    fun setAdminPasswordText(text: String) {
        _state.update { it.copy(adminPasswordText = text) }
    }

    fun setUserNameText(text: String) {
        _state.update { it.copy(userNameText = text) }
    }

    private fun canConfirm(): Boolean {
        val groupNameBlank = state.value.groupNameText.isBlank()
        val memberPasswordBlank = state.value.memberPasswordText.isBlank()
        val adminPasswordBlank = state.value.adminPasswordText.isBlank()
        val userNameBlank = state.value.userNameText.isBlank()
        if (groupNameBlank) {
            _state.update { it.copy(groupNameError = "Please enter the group name") }
        }
        if (memberPasswordBlank) {
            _state.update { it.copy(memberPasswordError = "Please enter the member password") }
        }
        if (adminPasswordBlank) {
            _state.update { it.copy(adminPasswordError = "Please enter the admin password") }
        }
        if (userNameBlank) {
            _state.update { it.copy(userNameError = "Please enter the user name") }
        }
        val canConfirm =
            !(groupNameBlank || memberPasswordBlank || adminPasswordBlank || userNameBlank)
        return canConfirm
    }

    private fun clearFieldsErrors() {
        _state.update {
            it.copy(
                groupNameError = null,
                memberPasswordError = null,
                adminPasswordError = null,
                userNameError = null
            )
        }
    }

    fun createAnonymousGroup(connectionSettingsId: Long) {
        viewModelScope.launch {
            if (!canConfirm()) {
                return@launch
            }
            _state.update { it.copy(showLoadingOverlay = true) }
            clearFieldsErrors()
            try {
                anonymousGroupService.createAnonymousGroup(
                    connectionSettingsId = connectionSettingsId,
                    groupName = state.value.groupNameText,
                    memberName = state.value.userNameText,
                    memberPassword = state.value.memberPasswordText,
                    adminPassword = state.value.adminPasswordText
                )
                _state.update { it.copy(showLoadingOverlay = false) }
                _navigateBackEvents.trySend(Unit)
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't create anonymous group",
                        errorInfo = ErrorInfo.fromException(e)
                    )
                )
                _state.update { it.copy(showLoadingOverlay = false) }
            }

        }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(errorInfo = null) }
    }

    fun showErrorDialog(errorInfo: ErrorInfo) {
        _state.update { it.copy(errorInfo = errorInfo) }
    }
}