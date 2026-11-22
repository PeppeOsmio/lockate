package com.peppeosmio.lockate.ui.screens.join_anonymous_group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.exceptions.InviteAlreadyClaimedException
import com.peppeosmio.lockate.exceptions.InviteExpiredException
import com.peppeosmio.lockate.exceptions.InviteNotFoundException
import com.peppeosmio.lockate.exceptions.InviteRejectedException
import com.peppeosmio.lockate.exceptions.LocalAGExistsException
import com.peppeosmio.lockate.exceptions.TooManyRequestsException
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec
import com.peppeosmio.lockate.service.invite.InviteCode
import com.peppeosmio.lockate.service.invite.InviteLink
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

/** A server this phone doesn't have yet, from an invite's QR code: it is added before joining */
data class NewServerInvite(val server: String, val apiKey: String?, val code: String)

class JoinAnonymousGroupViewModel(
    private val inviteService: InviteService,
    private val connectionService: ConnectionService,
) : ViewModel() {
    private val _state = MutableStateFlow(JoinAnonymousGroupState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>(Channel.BUFFERED)
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    // after joining, the map opens on the group, on whichever server it is
    private val _navigateHomeEvents = Channel<Unit>(Channel.BUFFERED)
    val navigateHomeEvents = _navigateHomeEvents.receiveAsFlow()
    private val _addServerEvents = Channel<NewServerInvite>(Channel.BUFFERED)
    val addServerEvents = _addServerEvents.receiveAsFlow()
    private var joinJob: Job? = null

    // the server to join on: the one in use, or the one of a scanned QR code
    private var connectionId: Long? = null

    fun load(connectionId: Long, code: String?) {
        if (this.connectionId != null) return
        this.connectionId = connectionId
        code?.let { setCodeText(it) }
    }

    /**
     * Reads a scanned QR code: on a saved server, the code is filled in and the join will happen
     * there (that server's own API key is kept); on a new server, that server is added first.
     */
    fun onScanned(text: String) {
        val link = InviteLink.parse(text)
        if (link == null) {
            _snackbarEvents.trySend(SnackbarErrorMessage("This QR code isn't a Lockate invite"))
            return
        }
        viewModelScope.launch {
            val saved = connectionService.findByUrl(link.server)
            if (saved == null) {
                _addServerEvents.trySend(
                    NewServerInvite(link.server, link.apiKey, link.code.toString())
                )
                return@launch
            }
            connectionId = saved.id
            setCodeText(link.code.toString())
        }
    }

    fun onScanFailed() {
        _snackbarEvents.trySend(SnackbarErrorMessage("Can't open the QR code scanner"))
    }

    fun setCodeText(text: String) {
        _state.update { it.copy(codeText = text, codeError = null) }
    }

    fun setUserNameText(text: String) {
        _state.update {
            it.copy(memberNameText = text.take(PayloadCodec.MAX_NAME_CHARS), memberNameError = null)
        }
    }

    fun joinAnonymousGroup() {
        if (joinJob?.isActive == true) return
        val connectionSettingsId = connectionId ?: return
        val code = InviteCode.parse(state.value.codeText)
        val memberName = state.value.memberNameText.trim()
        _state.update {
            it.copy(
                codeError = if (code == null) "Enter a code like 7-word-word-word" else null,
                memberNameError =
                    if (memberName.isEmpty()) "Please enter your member name" else null,
            )
        }
        if (code == null || memberName.isEmpty()) return
        joinJob = viewModelScope.launch {
            _state.update { it.copy(step = JoinStep.CheckingCode) }
            try {
                val group =
                    inviteService.joinWithCode(
                        connectionId = connectionSettingsId,
                        code = code,
                        memberName = memberName,
                        onCodeConfirmed = {
                            _state.update { it.copy(step = JoinStep.ReceivingKey) }
                        },
                    )
                connectionService.saveSelectedConnectionSettingsId(connectionSettingsId)
                connectionService.saveLastAGInternalId(connectionSettingsId, group.internalId)
                _navigateHomeEvents.trySend(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    when (e) {
                        is InviteNotFoundException ->
                            SnackbarErrorMessage(
                                "There's no invite with this number. It may have expired."
                            )

                        is InviteAlreadyClaimedException ->
                            SnackbarErrorMessage(
                                "Someone else already used this code. Ask for a new one."
                            )

                        is InviteRejectedException ->
                            SnackbarErrorMessage(
                                "The code is wrong or the invite was cancelled. Ask for a new one."
                            )

                        is InviteExpiredException ->
                            SnackbarErrorMessage("The invite expired. Ask for a new one.")

                        is TooManyRequestsException ->
                            SnackbarErrorMessage("Too many attempts. Try again in a minute.")

                        is LocalAGExistsException ->
                            SnackbarErrorMessage("You already joined this anonymous group")

                        else ->
                            SnackbarErrorMessage(
                                text = "Can't join anonymous group",
                                errorInfo = ErrorInfo.fromException(e),
                            )
                    }
                )
            } finally {
                _state.update { it.copy(step = null) }
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
