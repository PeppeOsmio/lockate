package com.peppeosmio.lockate.ui.screens.join_anonymous_group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peppeosmio.lockate.exceptions.LocalAGExistsException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
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

class JoinAnonymousGroupViewModel(
    private val anonymousGroupService: AnonymousGroupService
) : ViewModel() {
    private val _state = MutableStateFlow(JoinAnonymousGroupState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _navigateBackEvents = Channel<Unit>()
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()

    fun setIdText(text: String) {
        _state.update { it.copy(idText = text) }
    }

    fun setMemberPasswordText(text: String) {
        _state.update { it.copy(memberPasswordText = text) }
    }

    fun setUserNameText(text: String) {
        _state.update { it.copy(memberNameText = text) }
    }

    private fun canConfirm(): Boolean {
        val idBlank = state.value.idText.isBlank()
        val memberPasswordBlank = state.value.memberPasswordText.isBlank()
        val memberNameBlank = state.value.memberNameText.isBlank()
        if (idBlank) {
            _state.update { it.copy(idError = "Please enter the group id") }
        }
        if (memberPasswordBlank) {
            _state.update { it.copy(memberPasswordError = "Please enter the member password") }
        }
        if (memberNameBlank) {
            _state.update { it.copy(memberNameError = "Please enter your member name") }
        }
        val canConfirm = !(idBlank || memberPasswordBlank || memberNameBlank)
        return canConfirm
    }

    private fun clearTextErrors() {
        _state.update {
            it.copy(
                idError = null,
                memberPasswordError = null,
                memberNameError = null,
                dialogErrorInfo = null
            )
        }
    }

    fun joinAnonymousGroup(connectionSettingsId: Long) {
        viewModelScope.launch {
            if (!canConfirm()) {
                return@launch
            }
            clearTextErrors()
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                anonymousGroupService.authMember(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupId = state.value.idText.trim(),
                    memberName = state.value.memberNameText.trim(),
                    memberPassword = state.value.memberPasswordText.trim()
                )
                _state.update { it.copy(showLoadingOverlay = false) }
                _navigateBackEvents.trySend(Unit)
            } catch (e: Exception) {
                val snackbarErrorMessage = when (e) {
                    is LocalAGExistsException -> SnackbarErrorMessage(
                        "You already joined this anonymous group"
                    )

                    is UnauthorizedException -> SnackbarErrorMessage(
                        "The member password provided is invalid."
                    )

                    else -> SnackbarErrorMessage(
                        text = "Can't join anonymous group", errorInfo = ErrorInfo.fromException(e)
                    )
                }
                _state.update { it.copy(showLoadingOverlay = false) }
                _snackbarEvents.trySend(snackbarErrorMessage)
            }
        }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogErrorInfo = null) }
    }

    fun showErrorDialog(errorInfo: ErrorInfo) {
        _state.update { it.copy(dialogErrorInfo = errorInfo) }
    }
}
