package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.anonymous_group.AGLocation
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.location.Location
import com.peppeosmio.lockate.service.location.LocationService
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.ErrorDialogInfo
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
    private val _mapLocationEvents = Channel<Location>()
    val mapLocationEvents = _mapLocationEvents.receiveAsFlow()


    fun getInitialDetails(anonymousGroupId: String, connectionSettingsId: Long) {
        viewModelScope.launch {
            runCatching { getCurrentLocation() }
        }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    getLocalAG(anonymousGroupId)
                    getLocalMembers()
                    remoteOperations(connectionSettingsId)
                }
            }
        }
    }

    suspend fun remoteOperations(connectionSettingsId: Long) {
        _state.update { it.copy(reloadData = false) }
        try {
            coroutineScope {
                launch {
                    streamLocations(connectionSettingsId)
                }
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
            _state.update { it.copy(reloadData = true) }
        }
    }

    @Throws
    suspend fun getCurrentLocation() {
        val result = ErrorHandler.runAndHandleException {
            locationService.getCurrentLocation()
        }
        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorDialogInfo = result.errorDialogInfo
                )
            )
        } else if (result.value == null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    "Geolocation is disabled", errorDialogInfo = null
                )
            )
        } else {
            _mapLocationEvents.trySend(result.value)
        }
    }

    @Throws
    private suspend fun getLocalAG(anonymousGroupId: String) {
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.getAnonymousGroupById(anonymousGroupId)
        }
        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't get local anonymous group",
                    errorDialogInfo = result.errorDialogInfo
                )
            )
            result.errorDialogInfo.exception?.let { throw it }
        } else {
            _state.update {
                it.copy(
                    anonymousGroup = result.value,
                )
            }
        }
    }

    private fun membersToLocationMap(members: List<AGMember>): Map<String, AGLocation> {
        return members.filter { member ->
            member.lastLocation != null && member.id != state.value.anonymousGroup!!.memberId
        }.associate { member ->
            member.id to member.lastLocation!!
        }
    }

    @Throws
    private suspend fun getLocalMembers() {
        val currentState = state.value
        if (currentState.anonymousGroup == null) {
            return
        }
        Log.d("", "Fetching local members...")
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.getLocalAGMembers(currentState.anonymousGroup.id)
        }

        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't get local members", errorDialogInfo = result.errorDialogInfo
                )
            )
            result.errorDialogInfo.exception?.let { throw it }
            return
        } else {
            Log.d("", "Local members: ${result.value}")
            val map = membersToLocationMap(result.value!!)
            _state.update {
                it.copy(
                    members = result.value, membersLocation = map
                )
            }
            Log.d(
                "", "Found ${result.value.size} members and ${map.size} locations"
            )
            return
        }
    }

    @Throws
    private suspend fun getRemoteMembers(connectionSettingsId: Long) {
        val result = ErrorHandler.runAndHandleException {
            handleMembersWithSameName(
                anonymousGroupService.getRemoteAGMembers(
                    anonymousGroupId = state.value.anonymousGroup!!.id,
                    connectionSettingsId = connectionSettingsId
                )
            )
        }
        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorDialogInfo = result.errorDialogInfo
                )
            )
            result.errorDialogInfo.exception?.let { throw it }
        } else {
            Log.d("", "Remote members: ${result.value!!.map { it.id }}")
            val map = membersToLocationMap(result.value!!)
            Log.d("", "Remote members to location: $map")
            _state.update {
                it.copy(
                    members = result.value, membersLocation = map
                )
            }
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
        if (result.errorDialogInfo != null) {
            result.errorDialogInfo.exception?.let { throw it }
        }
    }

    suspend fun authAdmin(connectionSettingsId: Long) {
        val currentState = state.value
        if (currentState.anonymousGroup == null || currentState.adminPasswordText.isBlank()) {
            return
        }
        _state.update { it.copy(showLoadingOverlay = true) }
        val result = ErrorHandler.runAndHandleException(customHandler = { e ->
            when (e) {
                is UnauthorizedException -> ErrorDialogInfo(
                    title = "Wrong password",
                    body = "The admin password you entered is incorrect.",
                    exception = e
                )

                else -> throw e
            }
        }) {
            anonymousGroupService.getAdminToken(
                connectionSettingsId = connectionSettingsId,
                anonymousGroupId = currentState.anonymousGroup.id,
                adminPassword = currentState.adminPasswordText
            )
            true
        }
        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorDialogInfo = result.errorDialogInfo
                )
            )
            _state.update { it.copy(showLoadingOverlay = false) }
        } else {
            _state.update { it.copy(showLoadingOverlay = false, isAdminTokenValid = true) }
        }

        getLocalAG(anonymousGroupId = currentState.anonymousGroup.id)
    }

    fun setAdminPasswordText(text: String) {
        _state.update { it.copy(adminPasswordText = text) }
    }

    @Throws
    private suspend fun streamLocations(connectionSettingsId: Long) {
        if (state.value.anonymousGroup == null) {
            return
        }
        Log.d("", "Streaming locations...")
        while (true) {
            val result = ErrorHandler.runAndHandleException {
                anonymousGroupService.streamLocations(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupId = state.value.anonymousGroup!!.id
                ).collect { locationUpdate ->
                    if (state.value.membersLocation == null) {
                        return@collect
                    }
                    if (locationUpdate.agMemberId !in state.value.membersLocation!!.keys) {
                        Log.d(
                            "", "Refetching members (new member ${locationUpdate.agMemberId})"
                        )
                        getRemoteMembers(connectionSettingsId)
                    }
                    _state.update {
                        it.copy(
                            membersLocation = it.membersLocation!! + (locationUpdate.agMemberId to locationUpdate.location)
                        )
                    }
                }
            }
            if (result.errorDialogInfo != null) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        "Connection error", errorDialogInfo = result.errorDialogInfo
                    )
                )
                result.errorDialogInfo.exception?.let { throw it }
            }
            delay(10000L)
        }
    }

    fun showDeleteAGDialog() {
        _state.update { it.copy(showDeleteAGDialog = true) }
    }

    fun hideDeleteAGDialog() {
        _state.update { it.copy(showDeleteAGDialog = false) }
    }

    @Throws
    suspend fun deleteAnonymousGroup(
        connectionSettingsId: Long, anonymousGroupId: String
    ): Boolean {
        _state.update { it.copy(showLoadingOverlay = true) }
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.deleteAnonymousGroup(
                connectionSettingsId = connectionSettingsId, anonymousGroupId = anonymousGroupId
            )
        }
        if (result.errorDialogInfo != null) {
            _state.update { it.copy(showLoadingOverlay = false) }
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Connection error", errorDialogInfo = result.errorDialogInfo
                )
            )
            return false
        }
        _state.update { it.copy(showLoadingOverlay = false) }
        return true
    }

    fun onOptionsButtonTap() {
        _state.update { it.copy(isDropdownMenuOpen = !it.isDropdownMenuOpen) }
    }

    fun openDropDownMenu() {
        _state.update { it.copy(isDropdownMenuOpen = true) }
    }

    fun closeDropdownMenu() {
        _state.update { it.copy(isDropdownMenuOpen = false) }
    }

    suspend fun toggleAGShareLocation() {
        if (state.value.anonymousGroup == null) {
            return
        }
        val newSendLocation = !state.value.anonymousGroup!!.sendLocation
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.setAGShareLocation(
                anonymousGroupId = state.value.anonymousGroup!!.id, sendLocation = newSendLocation
            )
        }
        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't set trySend location", errorDialogInfo = result.errorDialogInfo
                )
            )
        } else {
            _state.update { it.copy(anonymousGroup = it.anonymousGroup!!.copy(sendLocation = newSendLocation)) }
        }
    }

    fun hideErrorDialog() {
        _state.update { it.copy(dialogErrorDialogInfo = null) }
    }

    fun showErrorDialog(errorDialogInfo: ErrorDialogInfo) {
        _state.update { it.copy(dialogErrorDialogInfo = errorDialogInfo) }
    }

    fun hideReloadDataButton() {
        _state.update { it.copy(reloadData = false) }
    }

    suspend fun locateMember(index: Int): Boolean {
        if (state.value.membersLocation == null || state.value.members == null) {
            return false
        }
        val result = ErrorHandler.runAndHandleException {
            val agMember = state.value.members!![index]
            state.value.membersLocation!![agMember.id]?.let { location ->
                Log.d("", "Locating member ${agMember.id}: $location")
                _mapLocationEvents.trySend(location.coordinates)
            }
        }
        if (result.errorDialogInfo != null) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Member with index $index does not exist!",
                    errorDialogInfo = result.errorDialogInfo
                )
            )
            return false
        } else {
            return true
        }
    }
}