package com.peppeosmio.lockate.ui.screens.join_anonymous_group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.exceptions.InviteAlreadyClaimedException
import com.peppeosmio.lockate.exceptions.InviteExpiredException
import com.peppeosmio.lockate.exceptions.InviteNotFoundException
import com.peppeosmio.lockate.exceptions.InviteRejectedException
import com.peppeosmio.lockate.exceptions.LocalAGExistsException
import com.peppeosmio.lockate.exceptions.TooManyRequestsException
import com.peppeosmio.lockate.service.invite.InviteCode
import com.peppeosmio.lockate.service.invite.InviteService
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JoinAnonymousGroupViewModel(
    private val inviteService: InviteService
) : ViewModel() {
    private val _state = MutableStateFlow(JoinAnonymousGroupState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _navigateBackEvents = Channel<Unit>()
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()
    private var joinJob: Job? = null

    fun setCodeText(text: String) {
        _state.update { it.copy(codeText = text, codeError = null) }
    }

    fun setUserNameText(text: String) {
        _state.update { it.copy(memberNameText = text, memberNameError = null) }
    }

    fun joinAnonymousGroup(connectionSettingsId: Long) {
        if (joinJob?.isActive == true) return
        val code = InviteCode.parse(state.value.codeText)
        val memberName = state.value.memberNameText.trim()
        _state.update {
            it.copy(
                codeError = if (code == null) "Enter a code like 7-word-word-word" else null,
                memberNameError = if (memberName.isEmpty()) "Please enter your member name" else null,
            )
        }
        if (code == null || memberName.isEmpty()) return
        joinJob = viewModelScope.launch {
            _state.update { it.copy(isJoining = true) }
            try {
                inviteService.joinWithCode(
                    connectionId = connectionSettingsId, code = code, memberName = memberName
                )
                _navigateBackEvents.trySend(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    when (e) {
                        is InviteNotFoundException -> SnackbarErrorMessage(
                            "There's no invite with this number. It may have expired."
                        )

                        is InviteAlreadyClaimedException -> SnackbarErrorMessage(
                            "Someone else already used this code. Ask for a new one."
                        )

                        is InviteRejectedException -> SnackbarErrorMessage(
                            "The code is wrong or the invite was cancelled. Ask for a new one."
                        )

                        is InviteExpiredException -> SnackbarErrorMessage(
                            "The invite expired. Ask for a new one."
                        )

                        is TooManyRequestsException -> SnackbarErrorMessage(
                            "Too many attempts. Try again in a minute."
                        )

                        is LocalAGExistsException -> SnackbarErrorMessage(
                            "You already joined this anonymous group"
                        )

                        else -> SnackbarErrorMessage(
                            text = "Can't join anonymous group",
                            errorInfo = ErrorInfo.fromException(e)
                        )
                    }
                )
            } finally {
                _state.update { it.copy(isJoining = false) }
            }
        }
    }

    fun cancelJoin() {
        joinJob?.cancel()
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogError = null) }
    }

    fun showErrorDialog(error: SnackbarErrorMessage) {
        _state.update { it.copy(dialogError = error) }
    }
}
