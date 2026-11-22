package com.peppeosmio.lockate.ui.screens.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.utils.LockatePermission
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [asked]: the permissions the system dialog was already shown for. Android stops showing it after
 * the user refuses twice, so a second tap opens the app's page in the system settings instead.
 */
data class PermissionsState(
    val granted: Map<LockatePermission, Boolean> = emptyMap(),
    val asked: Set<LockatePermission> = emptySet(),
) {
    /** The one to do next, highlighted on screen */
    val next: LockatePermission? = LockatePermission.asked.firstOrNull { granted[it] == false }
}

class PermissionsViewModel(
    private val permissionsService: PermissionsService,
    private val anonymousGroupService: AnonymousGroupService,
) : ViewModel() {
    private val _state = MutableStateFlow(PermissionsState())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()
    private val _doneEvents = Channel<Unit>(Channel.BUFFERED)
    val doneEvents = _doneEvents.receiveAsFlow()
    private var groupInternalId: Long? = null
    private var isDone = false

    /** [groupInternalId]: the group whose sharing turns on once both location permissions are on */
    fun start(groupInternalId: Long?) {
        this.groupInternalId = groupInternalId
        refresh()
    }

    fun onAsked(permission: LockatePermission) {
        _state.update { it.copy(asked = it.asked + permission) }
    }

    /** Reads the permissions again, after a system dialog or a trip to the system settings */
    fun refresh() {
        _state.update { it.copy(granted = permissionsService.granted()) }
        val groupInternalId = groupInternalId ?: return
        if (isDone || !permissionsService.canShare()) return
        isDone = true
        viewModelScope.launch {
            val group = anonymousGroupService.getAGByInternalId(groupInternalId)
            // also when it already shares: the event starts the service that couldn't start before
            anonymousGroupService.setAGSendLocation(group, true)
            _doneEvents.send(Unit)
        }
    }
}
