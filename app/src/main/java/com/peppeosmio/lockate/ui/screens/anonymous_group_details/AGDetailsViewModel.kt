package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.exceptions.LocationDisabledException
import com.peppeosmio.lockate.exceptions.NoPermissionException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupEvent
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AGDetailsViewModel(
    private val anonymousGroupService: AnonymousGroupService,
    private val locationService: LocationService
) : ViewModel() {

    enum class AGDetailsTab {
        Map, Members
    }

    private val _state = MutableStateFlow(AGDetailsState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _cameraPositionEvents = Channel<Coordinates>()
    val cameraPositionEvents = _cameraPositionEvents.receiveAsFlow()
    private val _navigateBackEvents = Channel<Unit>()
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()
    private val _pagerEvents = Channel<AGDetailsTab>()
    val pagerEvents = _pagerEvents.receiveAsFlow()
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
                    val myId = state.value.anonymousGroup!!.memberId
                    val me = state.value.members!![myId] ?: return@collect
                    _state.update {
                        it.copy(
                            members = it.members!! + (myId to me.copy(lastLocationRecord = event.locationRecord))
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
                    })
                    _state.update { it.copy(remoteDataLoadingState = LoadingState.Success) }
                    while (true) {
                        try {
                            streamLocations(connectionSettingsId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Log.e(
                                "",
                                "Streaming location for connectionSettingsId=$connectionSettingsId failed, retrying in 10 s"
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
    @OptIn(ExperimentalTime::class)
    private fun listenForUserLocation() {
        if (state.value.anonymousGroup == null) {
            return
        }
        listenForUserLocationJob?.cancel()
        listenForUserLocationJob = viewModelScope.launch {
            try {
                Log.d("", "Streaming my position")
                locationService.getLocationUpdates().collect { coordinates ->
                    if (state.value.myLocationRecordFromGPS == null) {
                        _cameraPositionEvents.trySend(coordinates.first)
                    }
                    _state.update {
                        it.copy(
                            myLocationRecordFromGPS = Pair(
                                LocationRecord(
                                    coordinates = coordinates.first,
                                    timestamp = Clock.System.now().toLocalDateTime(
                                        TimeZone.UTC
                                    )
                                ), coordinates.second
                            )
                        )
                    }
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
    @OptIn(ExperimentalTime::class)
    @Throws
    private suspend fun getAndMoveToMyLocation() {
        Log.d("", "Getting my location")
        try {
            val coordinates = locationService.getCurrentLocation()
            if (coordinates != null) {
                _state.update {
                    it.copy(
                        myLocationRecordFromGPS = Pair(
                            LocationRecord(
                                coordinates = coordinates,
                                timestamp = Clock.System.now().toLocalDateTime(
                                    timeZone = TimeZone.UTC
                                )
                            ), it.myLocationRecordFromGPS?.second ?: 0f
                        )
                    )
                }
                _cameraPositionEvents.trySend(coordinates)
            } else if (state.value.myLocationRecordFromGPS != null) {
                _cameraPositionEvents.trySend(state.value.myLocationRecordFromGPS!!.first.coordinates)
            }
        } catch (e: Exception) {
            if (state.value.myLocationRecordFromGPS != null) {
                _cameraPositionEvents.trySend(state.value.myLocationRecordFromGPS!!.first.coordinates)
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
            val membersMap = members.associateBy { it.id }.toMutableMap()
            for (memberId in membersMap.keys) {
                val oldLocationRecord =
                    state.value.members!![memberId]?.lastLocationRecord ?: continue
                val newLocationRecord = membersMap[memberId]!!.lastLocationRecord!!
                if (oldLocationRecord.timestamp >= newLocationRecord.timestamp) {
                    membersMap[memberId] =
                        membersMap[memberId]!!.copy(lastLocationRecord = oldLocationRecord)
                }
            }
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
                    it.copy(
                        members = it.members!! + (member.id to member.copy(lastLocationRecord = locationUpdate.locationRecord))
                    )
                }
                if (locationUpdate.agMemberId == state.value.followedMemberId) {
                    _cameraPositionEvents.trySend(locationUpdate.locationRecord.coordinates)
                }
            }
        }
    }


    fun showDeleteAGDialog() {
        _state.update { it.copy(showDeleteAGDialog = true, isDropdownMenuOpen = false) }
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
                _state.update { it.copy(showLoadingOverlay = false, showDeleteAGDialog = false) }
                _navigateBackEvents.trySend(Unit)
            } catch (e: Exception) {
                _state.update { it.copy(showLoadingOverlay = false, showDeleteAGDialog = false) }
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

    fun onTapLocate(agMemberId: String) = viewModelScope.launch {
        if (state.value.anonymousGroup == null || state.value.members == null) {
            return@launch
        }
        state.value.members!![agMemberId]?.let { member ->
            if (agMemberId == state.value.anonymousGroup!!.memberId) {
                onTapMyLocation()
                _pagerEvents.send(AGDetailsTab.Map)
                return@launch
            }
            if (member.lastLocationRecord == null) {
                return@launch
            }
            _cameraPositionEvents.trySend(member.lastLocationRecord.coordinates)
            _pagerEvents.send(AGDetailsTab.Map)
        }
    }

    fun onTapFollow(agMemberId: String) = viewModelScope.launch {
        if (state.value.members == null || state.value.anonymousGroup == null) {
            return@launch
        }
        state.value.members!![agMemberId]?.let { member ->
            _state.update { it.copy(followedMemberId = agMemberId) }

            _state.update { it.copy(followedMemberId = agMemberId) }
            Log.d("", "Following member $agMemberId")
            if (member.lastLocationRecord == null) {
                return@launch
            }
            _cameraPositionEvents.trySend(member.lastLocationRecord.coordinates)

            _pagerEvents.send(AGDetailsTab.Map)
        }
    }


    fun stopFollowMember() {
        _state.update { it.copy(followedMemberId = null) }
    }
}