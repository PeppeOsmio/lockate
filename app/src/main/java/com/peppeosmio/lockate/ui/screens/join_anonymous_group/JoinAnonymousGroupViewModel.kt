package com.peppeosmio.lockate.ui.screens.join_anonymous_group

import androidx.lifecycle.ViewModel
import com.peppeosmio.lockate.exceptions.LocalAGExistsException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.ErrorDialogInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class JoinAnonymousGroupViewModel(
    private val anonymousGroupService: AnonymousGroupService
) : ViewModel() {
    private val _state = MutableStateFlow(JoinAnonymousGroupState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

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
                dialogErrorDialogInfo = null
            )
        }
    }

    suspend fun joinAnonymousGroup(connectionSettingsId: Long): String? {
        if (!canConfirm()) {
            return null
        }
        clearTextErrors()
        _state.update { it.copy(showLoadingOverlay = true) }
        val result = ErrorHandler.runAndHandleException(customHandler = { e ->
            when (e) {
                is LocalAGExistsException -> ErrorDialogInfo(
                    title = "Already joined",
                    body = "You already joined this anonymous group",
                    exception = e
                )

                is UnauthorizedException -> ErrorDialogInfo(
                    title = "Invalid member password",
                    body = "The member password provided is invalid.",
                    exception = e
                )


                else -> throw e
            }
        }) {
            anonymousGroupService.authMember(
                connectionSettingsId = connectionSettingsId,
                anonymousGroupId = state.value.idText.trim(),
                memberName = state.value.memberNameText.trim(),
                memberPassword = state.value.memberPasswordText.trim()
            )
        }
        _state.update { it.copy(showLoadingOverlay = false) }
        if (result.errorDialogInfo != null) {
            val snackbarErrorMessage = when (result.errorDialogInfo.exception) {
                is LocalAGExistsException, is UnauthorizedException -> SnackbarErrorMessage(
                    text = result.errorDialogInfo.body
                )

                else -> SnackbarErrorMessage(
                    "Can't join anonymous group", errorDialogInfo = result.errorDialogInfo
                )
            }
            _snackbarEvents.trySend(snackbarErrorMessage)
            return null
        } else {
            return state.value.idText.trim()
        }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogErrorDialogInfo = null) }
    }

    fun showErrorDialog(errorDialogInfo: ErrorDialogInfo) {
        _state.update { it.copy(dialogErrorDialogInfo = errorDialogInfo) }
    }
}
