package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class AnonymousGroupDetailsViewModel(
    private val anonymousGroupService: AnonymousGroupService,
    private val locationService: LocationService
) : ViewModel() {
    private val _state = MutableStateFlow(AnonymousGroupDetailsState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _mapCoordinatesEvents = Channel<Coordinates>()
    val mapLocationEvents = _mapCoordinatesEvents.receiveAsFlow()
    private val _navigateBackEvents = Channel<Unit>()
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()


    fun getInitialDetails(anonymousGroupId: String, connectionSettingsId: Long) {
        viewModelScope.launch {
            runCatching { getCurrentLocation() }
        }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    getLocalAG(anonymousGroupId)
                    getLocalMembers()
                    launch {
                        streamLocations(connectionSettingsId)
                    }
                    remoteOperations(connectionSettingsId)
                }
            }
        }
    }

    fun remoteOperations(connectionSettingsId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(reloadData = false) }
            try {
                coroutineScope {
                    launch {
                        _state.update { it.copy(showLoadingIcon = false) }
                        joinAll(launch {
                            getRemoteMembers(connectionSettingsId)
                        }, launch {
                            verifyAdminAuth(connectionSettingsId)
                        })
                        _state.update { it.copy(showLoadingIcon = false) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(showLoadingIcon = false, reloadData = true) }
            }
        }
    }

    @Throws
    fun getCurrentLocation() {
        viewModelScope.launch {
            val result = ErrorHandler.runAndHandleException {
                locationService.getCurrentLocation()
            }
            if (result.errorInfo != null) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Connection error", errorInfo = result.errorInfo
                    )
                )
            } else if (result.value == null) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        "Geolocation is disabled", errorInfo = null
                    )
                )
            } else {
                _mapCoordinatesEvents.trySend(result.value)
            }
        }
    }

    @Throws
    private suspend fun getLocalAG(anonymousGroupId: String) {
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.getAnonymousGroupById(anonymousGroupId)
        }
        if (result.errorInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't get local anonymous group", errorInfo = result.errorInfo
                )
            )
            result.errorInfo.exception?.let { throw it }
        } else {
            _state.update {
                it.copy(
                    anonymousGroup = result.value,
                )
            }
        }
    }


    @Throws
    private suspend fun getLocalMembers() {
        val currentState = state.value
        if (currentState.anonymousGroup == null) {
            return
        }
        Log.d("", "Fetching local members...")
        try {
            val members =
                handleMembersWithSameName(anonymousGroupService.getLocalAGMembers(currentState.anonymousGroup.id))
            Log.d("", "Local members: $members")
            val membersMap = members.associateBy { it.id }
            _state.update {
                it.copy(
                    members = membersMap
                )
            }
            return
        } catch (e: Exception) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't get local members", errorInfo = ErrorInfo.fromException(e)
                )
            )
            throw e
        }
    }

    @Throws
    private suspend fun getRemoteMembers(connectionSettingsId: Long) {
        try {
            val members = handleMembersWithSameName(
                anonymousGroupService.getRemoteAGMembers(
                    anonymousGroupId = state.value.anonymousGroup!!.id,
                    connectionSettingsId = connectionSettingsId
                )
            )
            Log.d("", "Remote members: ${members.map { it.id }}")
            val membersMap = members.associateBy { it.id }
            _state.update {
                it.copy(
                    members = membersMap
                )
            }
        } catch (e: Exception) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorInfo = ErrorInfo.fromException(e)
                )
            )
            throw e
        }
    }

    private fun handleMembersWithSameName(members: List<AGMember>): List<AGMember> {
        val counts = mutableMapOf<String, Int>()
        return members.map { member ->
            val count = counts.getOrDefault(member.name, 0)
            counts[member.name] = count + 1
            if (count == 0) {
                member // first occurrence, keep as is
            } else {
                member.copy(name = "${member.name} ($count)") // subsequent occurrences
            }
        }
    }

    @Throws
    private suspend fun verifyAdminAuth(connectionSettingsId: Long) {
        val currentState = state.value
        if (currentState.anonymousGroup == null) {
            return
        }
        _state.update { it.copy(showLoadingIcon = true) }
        Log.d("", "verifying admin auth")
        val result = ErrorHandler.runAndHandleException(customHandler = { e ->
            when (e) {
                is UnauthorizedException -> null

                else -> throw e
            }
        }) {
            anonymousGroupService.verifyAdminAuth(
                anonymousGroupId = currentState.anonymousGroup.id,
                connectionSettingsId = connectionSettingsId
            )
            true
        }
        val isAdminTokenValid = result.value != null
        _state.update {
            it.copy(isAdminTokenValid = isAdminTokenValid)
        }
        if (result.errorInfo != null) {
            result.errorInfo.exception?.let { throw it }
        }
    }

    fun authAdmin(connectionSettingsId: Long) {
        viewModelScope.launch {
            val currentState = state.value
            if (currentState.anonymousGroup == null || currentState.adminPasswordText.isBlank()) {
                return@launch
            }
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                anonymousGroupService.getAdminToken(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupId = currentState.anonymousGroup.id,
                    adminPassword = currentState.adminPasswordText
                )
            } catch (e: Exception) {
                when (e) {
                    is UnauthorizedException -> _snackbarEvents.trySend(
                        SnackbarErrorMessage(
                            text = "Incorrect admin password"
                        )
                    )

                    else -> _snackbarEvents.trySend(
                        SnackbarErrorMessage(
                            text = "Connection error", errorInfo = ErrorInfo.fromException(e)
                        )
                    )
                }
                _state.update { it.copy(showLoadingOverlay = false) }
                return@launch

            }
            _state.update { it.copy(showLoadingOverlay = false, isAdminTokenValid = true) }
            getLocalAG(anonymousGroupId = currentState.anonymousGroup.id)
        }
    }


    fun setAdminPasswordText(text: String) {
        _state.update { it.copy(adminPasswordText = text) }
    }

    private suspend fun streamLocations(connectionSettingsId: Long) {
        if (state.value.anonymousGroup == null) {
            return
        }
        while (true) {
            val result = ErrorHandler.runAndHandleException {
                anonymousGroupService.streamLocations(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupId = state.value.anonymousGroup!!.id
                ).collect { locationUpdate ->
                    Log.d("", "Received location: $locationUpdate")
                    if (state.value.members == null) {
                        return@collect
                    }
                    if (locationUpdate.agMemberId !in state.value.members!!.keys) {
                        Log.d(
                            "", "Refetching members (new member ${locationUpdate.agMemberId})"
                        )
                        getRemoteMembers(connectionSettingsId)
                    }
                    val member = state.value.members!![locationUpdate.agMemberId]
                    if (member != null) {
                        _state.update {
                            val newMembers =
                                it.members!! + (locationUpdate.agMemberId to member.copy(
                                    lastLocationRecord = locationUpdate.locationRecord
                                ))
                            it.copy(
                                members = newMembers
                            )
                        }
                    }
                }
            }
            if (result.errorInfo != null) {
                result.errorInfo.exception?.printStackTrace()
                Log.d("", "Streaming location failed, retrying in 10 s")
                delay(10000L)
            }
        }
    }

    fun showDeleteAGDialog() {
        _state.update { it.copy(showDeleteAGDialog = true) }
    }

    fun hideDeleteAGDialog() {
        _state.update { it.copy(showDeleteAGDialog = false) }
    }

    @Throws
    fun deleteAnonymousGroup(
        connectionSettingsId: Long, anonymousGroupId: String
    ) {
        viewModelScope.launch {
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                anonymousGroupService.deleteAnonymousGroup(
                    connectionSettingsId = connectionSettingsId, anonymousGroupId = anonymousGroupId
                )
                _state.update { it.copy(showLoadingOverlay = false) }
                _navigateBackEvents.trySend(Unit)
            } catch (e: Exception) {
                _state.update { it.copy(showLoadingOverlay = false) }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Connection error", errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
        }
    }

    fun toggleDropdownMenu() {
        _state.update { it.copy(isDropdownMenuOpen = !it.isDropdownMenuOpen) }
    }

    fun closeDropdownMenu() {
        _state.update { it.copy(isDropdownMenuOpen = false) }
    }

    fun toggleAGShareLocation() {
        viewModelScope.launch {
            if (state.value.anonymousGroup == null) {
                return@launch
            }
            val newSendLocation = !state.value.anonymousGroup!!.sendLocation
            try {
                anonymousGroupService.setAGShareLocation(
                    anonymousGroupId = state.value.anonymousGroup!!.id,
                    sendLocation = newSendLocation
                )
                _state.update { it.copy(anonymousGroup = it.anonymousGroup!!.copy(sendLocation = newSendLocation)) }
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't set location", errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
        }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogErrorInfo = null) }
    }

    fun showErrorDialog(errorInfo: ErrorInfo) {
        _state.update { it.copy(dialogErrorInfo = errorInfo) }
    }

    fun hideReloadDataButton() {
        _state.update { it.copy(reloadData = false) }
    }

    fun findMember(agMemberId: String): Boolean {
        if (state.value.members == null) {
            return false
        }
        try {
            state.value.members!![agMemberId]?.let { member ->
                if (member.lastLocationRecord == null) {
                    return@let
                }
                Log.d("", "Locating member ${agMemberId}: ${member.lastLocationRecord}")
                _mapCoordinatesEvents.trySend(member.lastLocationRecord.coordinates)
            }
            return true
        } catch (e: Exception) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Member with id $agMemberId does not exist!",
                    errorInfo = ErrorInfo.fromException(e)
                )
            )
            return false
        }
    }
}