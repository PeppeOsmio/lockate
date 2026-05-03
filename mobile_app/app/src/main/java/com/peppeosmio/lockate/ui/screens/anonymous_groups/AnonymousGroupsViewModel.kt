package com.peppeosmio.lockate.ui.screens.anonymous_groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupEvent
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnonymousGroupsViewModel(
    private val anonymousGroupService: AnonymousGroupService
) : ViewModel() {

    private val _state = MutableStateFlow(AnonymousGroupsState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            collectAGEvents()
        }
    }

    fun setFetchedDataOfConnectionId(connectionId: Long) {
        _state.update { it.copy(fetchedDataOfConnectionId = connectionId) }
    }

    fun getInitialData(connectionSettingsId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching {
                coroutineScope {
                    getAnonymousGroups(connectionSettingsId)
                    verifyAGsMemberAuth(connectionSettingsId)
                }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    @Throws
    private suspend fun getAnonymousGroups(connectionSettingsId: Long) {
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.listLocalAnonymousGroups(
                connectionSettingsId
            )
        }
        if (result.errorInfo != null) {
            if (result.errorInfo.exception is CancellationException) {
                return
            }
            _state.update { it.copy(showLoadingOverlay = false) }
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't get local anonymous groups", errorInfo = result.errorInfo
                )
            )
            result.errorInfo.exception?.let { throw it }
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
                        anonymousGroupService.getAGByInternalId(event.anonymousGroupInternalId)
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

                is AnonymousGroupEvent.RemoteAGExistsEvent -> {
                    _state.update {
                        it.copy(anonymousGroups = it.anonymousGroups!!.map { ag ->
                            if (ag.id != event.anonymousGroupId) {
                                ag
                            } else {
                                ag.copy(existsRemote = true)
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

                is AnonymousGroupEvent.ReaddedToAnonymousGroupEvent -> {
                    _state.update {
                        it.copy(anonymousGroups = it.anonymousGroups!!.map { ag ->
                            if (ag.id != event.anonymousGroupId) {
                                ag
                            } else {
                                ag.copy(isMember = true)
                            }
                        })
                    }
                }


                else -> Unit
            }
        }
    }

    @Throws
    private suspend fun verifyAGsMemberAuth(connectionSettingsId: Long) {
        if (state.value.anonymousGroups == null) {
            return
        }
        state.value.anonymousGroups!!.forEach { anonymousGroup ->
            try {
                anonymousGroupService.verifyMemberAuth(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupInternalId = anonymousGroup.internalId
                )
            } catch (e: Exception) {
                e.printStackTrace()
                when (e) {
                    is UnauthorizedException, is RemoteAGNotFoundException -> Unit
                    else -> {
                        _snackbarEvents.trySend(
                            SnackbarErrorMessage(
                                text = "Connection error", errorInfo = ErrorInfo.fromException(e)
                            )
                        )
                        throw e
                    }
                }
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

    fun removeAnonymousGroup() {
        viewModelScope.launch {
            val currentState = state.value
            if (currentState.anonymousGroups == null || currentState.selectedAGIndex == null) {
                return@launch
            }
            _state.update { it.copy(showLoadingOverlay = true) }
            val anonymousGroup = currentState.anonymousGroups[currentState.selectedAGIndex]
            try {
                anonymousGroupService.deleteLocalAnonymousGroup(anonymousGroup)
                _state.update { it.copy(showLoadingOverlay = false) }
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't delete local anonymous group $anonymousGroup",
                        errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
        }
    }

    fun leaveAnonymousGroup(connectionSettingsId: Long) {
        viewModelScope.launch {
            val currentState = state.value
            if (currentState.selectedAGIndex == null) {
                _state.update { it.copy(showLoadingOverlay = false, showSureLeaveDialog = false) }
            }
            _state.update { it.copy(showLoadingOverlay = true, showSureLeaveDialog = false) }
            val anonymousGroup = currentState.anonymousGroups!![currentState.selectedAGIndex!!]
            try {
                anonymousGroupService.leaveAnonymousGroup(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupInternalId = anonymousGroup.internalId
                )
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't leave anonymous group $anonymousGroup",
                        errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
            _state.update {
                it.copy(
                    selectedAGIndex = null,
                    showLoadingOverlay = false,
                )
            }
        }
    }
}
