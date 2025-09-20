package com.peppeosmio.lockate.ui.screens.anonymous_groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupEvent
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnonymousGroupsViewModel(
    private val anonymousGroupService: AnonymousGroupService
) : ViewModel() {

    private val _state = MutableStateFlow(AnonymousGroupsState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    suspend fun getInitialData(connectionSettingsId: Long) = coroutineScope {
        launch { collectAGEvents() }
        runCatching {
            coroutineScope {
                getAnonymousGroups(connectionSettingsId)
                verifyAGsMemberAuth(connectionSettingsId)
            }
        }
    }

    @Throws
    private suspend fun getAnonymousGroups(connectionSettingsId: Long) {
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.listLocalAnonymousGroups(
                connectionSettingsId
            )
        }
        if (result.errorDialogInfo != null) {
            _state.update { it.copy(showLoadingOverlay = false) }
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't get local anonymous groups",
                    errorDialogInfo = result.errorDialogInfo
                )
            )
            result.errorDialogInfo.exception?.let { throw it }
        } else {
            _state.update { it.copy(anonymousGroups = result.value, showLoadingOverlay = false) }
        }

    }

    private suspend fun collectAGEvents() {
        anonymousGroupService.events.collect { event ->
            when (event) {
                is AnonymousGroupEvent.NewAnonymousGroupEvent -> {
                    val currentState = state.value
                    if (currentState.anonymousGroups == null) {
                        return@collect
                    }
                    val createdAnonymousGroup =
                        anonymousGroupService.getAnonymousGroupById(event.anonymousGroupId)
                    _state.update { it.copy(anonymousGroups = listOf(createdAnonymousGroup) + it.anonymousGroups!!) }
                }

                is AnonymousGroupEvent.DeleteAnonymousGroupEvent -> {
                    _state.update {
                        it.copy(anonymousGroups = it.anonymousGroups!!.filterNot { anonymousGroup ->
                            anonymousGroup.id == event.anonymousGroupId
                        })
                    }
                }

                is AnonymousGroupEvent.RemoteAGDoesntExistEvent -> {
                    _state.update {
                        it.copy(anonymousGroups = it.anonymousGroups!!.map { ag ->
                            if (ag.id != event.anonymousGroupId) {
                                ag
                            } else {
                                ag.copy(existsRemote = false)
                            }
                        })
                    }
                }

                is AnonymousGroupEvent.RemovedFromAnonymousGroupEvent -> {
                    _state.update {
                        it.copy(anonymousGroups = it.anonymousGroups!!.map { ag ->
                            if (ag.id != event.anonymousGroupId) {
                                ag
                            } else {
                                ag.copy(isMember = false)
                            }
                        })
                    }
                }
            }
        }
    }

    @Throws
    private suspend fun verifyAGsMemberAuth(connectionSettingsId: Long) {
        if (state.value.anonymousGroups == null) {
            return
        }
        state.value.anonymousGroups!!.forEach { anonymousGroup ->
            val result = ErrorHandler.runAndHandleException(customHandler = { e ->
                when (e) {
                    is UnauthorizedException -> null
                    else -> throw e
                }
            }) {
                anonymousGroupService.verifyMemberAuth(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupId = anonymousGroup.id
                )
            }
            if (result.errorDialogInfo != null) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Connection error", errorDialogInfo = result.errorDialogInfo
                    )
                )
                result.errorDialogInfo.exception?.let { throw it }
            }
        }
    }

    fun onFabTap() {
        _state.update { it.copy(showAddBottomSheet = true) }
    }

    fun onSearch(query: String) {}

    fun closeAddBottomSheet() {
        _state.update { it.copy(showAddBottomSheet = false) }
    }

    fun selectAnonymousGroup(index: Int) {
        _state.update {
            it.copy(
                selectedAGIndex = index
            )
        }
    }

    fun unselectAnonymousGroup() {
        _state.update {
            it.copy(
                selectedAGIndex = null
            )
        }
    }

    fun openSureLeaveDialog() {
        _state.update { it.copy(showSureLeaveDialog = true) }
    }

    fun closeSureLeaveDialog() {
        _state.update { it.copy(showSureLeaveDialog = false) }
    }

    suspend fun removeAnonymousGroup() {
        val currentState = state.value
        if (currentState.anonymousGroups == null || currentState.selectedAGIndex == null) {
            return
        }
        _state.update { it.copy(showLoadingOverlay = true) }
        val anonymousGroupId = currentState.anonymousGroups[currentState.selectedAGIndex].id
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.deleteLocalAnonymousGroup(anonymousGroupId)
        }
        _state.update { it.copy(showLoadingOverlay = false) }
        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't delete local anonymous group $anonymousGroupId",
                    errorDialogInfo = result.errorDialogInfo
                )
            )
        }
    }

    suspend fun leaveAnonymousGroup(connectionSettingsId: Long) {
        val currentState = state.value
        if (currentState.selectedAGIndex == null) {
            _state.update { it.copy(showLoadingOverlay = false, showSureLeaveDialog = false) }
        }
        _state.update { it.copy(showLoadingOverlay = true, showSureLeaveDialog = false) }
        val anonymousGroupId = currentState.anonymousGroups!![currentState.selectedAGIndex!!].id
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.leaveAnonymousGroup(
                connectionSettingsId = connectionSettingsId, anonymousGroupId = anonymousGroupId
            )
        }
        _state.update {
            it.copy(
                selectedAGIndex = null,
                showLoadingOverlay = false,
            )
        }
        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't leave anonymous group $anonymousGroupId",
                    errorDialogInfo = result.errorDialogInfo
                )
            )
        }
    }
}
