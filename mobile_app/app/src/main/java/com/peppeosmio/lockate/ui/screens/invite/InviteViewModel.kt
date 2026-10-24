package com.peppeosmio.lockate.ui.screens.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.invite.InviteService
import com.peppeosmio.lockate.service.invite.InviteState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InviteScreenState(
    val groupName: String? = null,
    val inviteState: InviteState = InviteState.Creating,
)

class InviteViewModel(
    private val anonymousGroupService: AnonymousGroupService,
    private val inviteService: InviteService,
) : ViewModel() {
    private val _state = MutableStateFlow(InviteScreenState())
    val state = _state.asStateFlow()
    private var inviteJob: Job? = null

    fun startInvite(anonymousGroupInternalId: Long) {
        inviteJob?.cancel()
        inviteJob = viewModelScope.launch {
            _state.update { it.copy(inviteState = InviteState.Creating) }
            val group = try {
                anonymousGroupService.getAGByInternalId(anonymousGroupInternalId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(inviteState = InviteState.Failed(e)) }
                return@launch
            }
            _state.update { it.copy(groupName = group.name) }
            inviteService.startInvite(group).collect { inviteState ->
                _state.update { it.copy(inviteState = inviteState) }
            }
        }
    }
}
