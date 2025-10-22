package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.exceptions.LocationDisabledException
import com.peppeosmio.lockate.exceptions.NoPermissionException
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupEvent
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.LoadingState
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

class AnonymousGroupDetailsViewModel(
    private val anonymousGroupService: AnonymousGroupService,
    private val locationService: LocationService
) : ViewModel() {
    private val _state = MutableStateFlow(AnonymousGroupDetailsState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _cameraPositionEvents = Channel<Coordinates>()
    val cameraPositionEvents = _cameraPositionEvents.receiveAsFlow()
    private val _navigateBackEvents = Channel<Unit>()
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()
    private var listenForUserLocationJob: Job? = null

    fun getInitialDetails(anonymousGroupInternalId: Long, connectionSettings: Long) {
        viewModelScope.launch {
            runCatching { getAndMoveToMyLocation() }
        }
        viewModelScope.launch {
            collectAGEvents()
        }
        viewModelScope.launch {
            runCatching {
                getLocalAG(anonymousGroupInternalId)
                getLocalMembers()
                remoteOperations(connectionSettings)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun collectAGEvents() {
        anonymousGroupService.events.collect { event ->
            when (event) {
                is AnonymousGroupEvent.AGLocationSentEvent -> {
                    if (state.value.members == null || state.value.anonymousGroup == null) {
                        return@collect
                    }
                    if (event.anonymousGroupId != state.value.anonymousGroup!!.id) {
                        return@collect
                    }
                    val me = state.value.members!![state.value.anonymousGroup!!.memberId]
                        ?: return@collect
                    if (me.lastLocationRecord == null) {
                        return@collect
                    }
                    _state.update {
                        val newMap = (it.members ?: emptyMap()).toMutableMap()
                        newMap += me.id to me.copy(
                            lastLocationRecord = me.lastLocationRecord.copy(
                                timestamp = event.timestamp.toLocalDateTime(
                                    TimeZone.UTC
                                )
                            )
                        )
                        it.copy(
                            members = newMap
                        )
                    }
                }

                else -> Unit
            }
        }
    }

    fun remoteOperations(connectionSettingsId: Long) {
        viewModelScope.launch {
            try {
                coroutineScope {
                    _state.update { it.copy(remoteDataLoadingState = LoadingState.IsLoading) }
                    joinAll(launch {
                        getRemoteMembers(connectionSettingsId)
                    }, launch {
                        verifyAdminAuth(connectionSettingsId)
                    })
                    _state.update { it.copy(remoteDataLoadingState = LoadingState.Success) }
                    while (true) {
                        try {
                            streamLocations(connectionSettingsId)
                        } catch (e: Exception) {
                            Log.e(
                                "",
                                "Streaming location for $connectionSettingsId failed, retrying in 10 s"
                            )
                            delay(10000L)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(remoteDataLoadingState = LoadingState.Failed) }
            }
        }
    }

    /**
     * Listens for the user's location, if called multiple times the previous job is canceled
     */
    private fun listenForUserLocation() {
        listenForUserLocationJob?.cancel()
        listenForUserLocationJob = viewModelScope.launch {
            try {
                Log.d("", "Streaming my position")
                locationService.getLocationUpdates().collect { coordinates ->
                    if (state.value.myCoordinates == null) {
                        _cameraPositionEvents.trySend(coordinates)
                    }
                    _state.update { it.copy(myCoordinates = coordinates) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Get the newest current location if possible otherwise move to the old one if available.
     * If there is an exception propagate it.
     */
    @Throws
    private suspend fun getAndMoveToMyLocation() {
        Log.d("", "Getting my location")
        try {
            val coordinates = locationService.getCurrentLocation()
            if (coordinates != null) {
                _state.update { it.copy(myCoordinates = coordinates) }
                _cameraPositionEvents.trySend(coordinates)
            } else if (state.value.myCoordinates != null) {
                _cameraPositionEvents.trySend(state.value.myCoordinates!!)
            }
        } catch (e: Exception) {
            if (state.value.myCoordinates != null) {
                _cameraPositionEvents.trySend(state.value.myCoordinates!!)
            }
            throw e
        }
        listenForUserLocation()
    }

    fun onTapMyLocation() {
        viewModelScope.launch {
            try {
                getAndMoveToMyLocation()
            } catch (e: LocationDisabledException) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        "Geolocation is disabled", errorInfo = null
                    )
                )
            } catch (e: NoPermissionException) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        "No location permissions"
                    )
                )
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        "Can't get location", errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
        }
    }

    @Throws
    private suspend fun getLocalAG(anonymousGroupInternalId: Long) {
        val result = ErrorHandler.runAndHandleException {
            anonymousGroupService.getAGByInternalId(anonymousGroupInternalId)
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


    /**
     * Sets state.members to the local members
     */
    @Throws
    private suspend fun getLocalMembers() {
        if (state.value.anonymousGroup == null) {
            return
        }
        Log.d("", "Fetching local members...")
        try {
            val members = handleMembersWithSameName(
                anonymousGroupService.getLocalAGMembers(state.value.anonymousGroup!!)
            )
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

    /**
     * Updates state.members with the remote members
     */
    @Throws
    private suspend fun getRemoteMembers(connectionSettingsId: Long) {
        try {
            val members = handleMembersWithSameName(
                anonymousGroupService.getRemoteAGMembers(
                    anonymousGroupInternalId = state.value.anonymousGroup!!.internalId,
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
        if (state.value.anonymousGroup == null) {
            return
        }
        Log.d("", "verifying admin auth")
        val result = ErrorHandler.runAndHandleException(customHandler = { e ->
            when (e) {
                is UnauthorizedException -> null

                else -> throw e
            }
        }) {
            anonymousGroupService.verifyAdminAuth(
                anonymousGroupInternalId = state.value.anonymousGroup!!.internalId,
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
            if (state.value.anonymousGroup == null || state.value.adminPasswordText.isBlank()) {
                return@launch
            }
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                anonymousGroupService.getAdminToken(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupInternalId = state.value.anonymousGroup!!.internalId,
                    adminPassword = state.value.adminPasswordText
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
            getLocalAG(anonymousGroupInternalId = state.value.anonymousGroup!!.internalId)
        }
    }


    fun setAdminPasswordText(text: String) {
        _state.update { it.copy(adminPasswordText = text) }
    }

    @Throws
    private suspend fun streamLocations(connectionSettingsId: Long) {
        anonymousGroupService.streamLocations(
            connectionSettingsId = connectionSettingsId,
            anonymousGroupInternalId = state.value.anonymousGroup!!.internalId
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
                    val newMembers = it.members!! + (locationUpdate.agMemberId to member.copy(
                        lastLocationRecord = locationUpdate.locationRecord
                    ))
                    it.copy(
                        members = newMembers
                    )
                }
                if (locationUpdate.agMemberId == state.value.followedMemberId) {
                    _cameraPositionEvents.trySend(locationUpdate.locationRecord.coordinates)
                }
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
        connectionSettingsId: Long, anonymousGroupInternalId: Long
    ) {
        viewModelScope.launch {
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                anonymousGroupService.deleteAnonymousGroup(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupInternalId = anonymousGroupInternalId
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
                anonymousGroupService.setAGSendLocation(
                    anonymousGroup = state.value.anonymousGroup!!, sendLocation = newSendLocation
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

    fun onTapMember(agMemberId: String) = viewModelScope.launch {
        if (state.value.members == null || state.value.anonymousGroup == null) {
            return@launch
        }
        state.value.members!![agMemberId]?.let { member ->
            if (member.lastLocationRecord == null) {
                _state.update { it.copy(followedMemberId = null) }
                return@let
            }
            if (member.id == state.value.anonymousGroup!!.memberId) {
                _state.update { it.copy(followedMemberId = null) }
                onTapMyLocation()
                return@launch
            }
            _state.update { it.copy(followedMemberId = agMemberId) }
            Log.d("", "Following member ${agMemberId}: ${member.lastLocationRecord}")
            _cameraPositionEvents.trySend(member.lastLocationRecord.coordinates)
        }
    }


    fun stopFollowMember() {
        _state.update { it.copy(followedMemberId = null) }
    }
}