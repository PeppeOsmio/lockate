package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.anonymous_group.AGChannelFrame
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.ChannelState
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.exceptions.AGMemberNotAdminException
import com.peppeosmio.lockate.exceptions.LocationDisabledException
import com.peppeosmio.lockate.exceptions.NoPermissionException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import com.peppeosmio.lockate.platform_service.ConnectivityService
import com.peppeosmio.lockate.platform_service.DeviceOrientationService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupEvent
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.utils.ErrorHandler
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.LoadingState
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Where the map should go: [fitRadiusMeters] zooms to show a whole circle, otherwise the zoom is fixed. */
data class CameraTarget(val coordinates: Coordinates, val fitRadiusMeters: Double? = null)

class AGDetailsViewModel(
    private val anonymousGroupService: AnonymousGroupService,
    private val locationService: LocationService,
    private val connectivityService: ConnectivityService,
    private val deviceOrientationService: DeviceOrientationService
) : ViewModel() {

    private val _state = MutableStateFlow(AGDetailsState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    // conflated so that a camera move requested before the map starts collecting isn't lost
    private val _cameraPositionEvents = Channel<CameraTarget>(Channel.CONFLATED)
    val cameraPositionEvents = _cameraPositionEvents.receiveAsFlow()
    private var listenForUserLocationJob: Job? = null
    private var listenForDeviceOrientationJob: Job? = null
    private var groupJob: Job? = null
    private var remoteJob: Job? = null
    private var connectionId: Long? = null

    init {
        viewModelScope.launch {
            collectAGEvents()
        }
        startMyLocation()
    }

    // Going back to the map from the circles page starts the location again, and moving the camera
    // to the phone would undo showing a circle
    private var skipNextMoveToMyLocation = false

    fun startMyLocation() {
        val moveCamera = !skipNextMoveToMyLocation
        skipNextMoveToMyLocation = false
        viewModelScope.launch {
            runCatching { getAndMoveToMyLocation(moveCamera) }
        }
    }

    /**
     * Loads the given group, cancelling any work done for the previously selected one.
     * A null [anonymousGroupInternalId] means no group is selected.
     */
    fun selectGroup(connectionId: Long, anonymousGroupInternalId: Long?) {
        groupJob?.cancel()
        remoteJob?.cancel()
        this.connectionId = connectionId
        _state.update {
            it.copy(
                anonymousGroup = null,
                members = null,
                circles = emptyList(),
                selectedCircleId = null,
                selectedMemberId = null,
                isFollowingSelectedMember = false,
                makeAdminDialogMemberId = null,
                remoteDataLoadingState = LoadingState.IsLoading,
                locationStreamState = LocationStreamState.Connecting
            )
        }
        if (anonymousGroupInternalId == null) {
            return
        }
        groupJob = viewModelScope.launch {
            try {
                getLocalAG(anonymousGroupInternalId)
                getLocalMembers()
                getLocalCircles()
                remoteOperations()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun collectAGEvents() {
        anonymousGroupService.events.collect { event ->
            when (event) {
                is AnonymousGroupEvent.ShowCircleOnMapEvent -> {
                    if (event.anonymousGroupId != state.value.anonymousGroup?.id) {
                        return@collect
                    }
                    _state.update {
                        it.copy(isFollowingSelectedMember = false, selectedCircleId = event.circle.id)
                    }
                    skipNextMoveToMyLocation = true
                    _cameraPositionEvents.trySend(
                        CameraTarget(event.circle.center, fitRadiusMeters = event.circle.radiusMeters)
                    )
                }

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

    fun remoteOperations() {
        val anonymousGroup = state.value.anonymousGroup ?: return
        remoteJob?.cancel()
        remoteJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    remoteDataLoadingState = LoadingState.IsLoading,
                    locationStreamState = LocationStreamState.Connecting
                )
            }
            // subscribed before the channel is held, so the snapshot of a new connection isn't missed
            launch(start = CoroutineStart.UNDISPATCHED) {
                anonymousGroupService.groupFrames
                    .filter { it.anonymousGroupInternalId == anonymousGroup.internalId }
                    .collect { applyFrame(it.frame) }
            }
            anonymousGroupService.holdChannel(anonymousGroup, send = false) { status ->
                _state.update {
                    it.copy(
                        remoteDataLoadingState = when (status.state) {
                            ChannelState.Connecting -> it.remoteDataLoadingState
                            ChannelState.Connected -> LoadingState.Success
                            ChannelState.Reconnecting -> if (it.remoteDataLoadingState == LoadingState.IsLoading) LoadingState.Failed else it.remoteDataLoadingState
                            ChannelState.Stopped -> LoadingState.Failed
                        }, locationStreamState = when (status.state) {
                            ChannelState.Connecting -> LocationStreamState.Connecting
                            ChannelState.Connected -> LocationStreamState.Connected
                            ChannelState.Reconnecting -> LocationStreamState.Reconnecting
                            ChannelState.Stopped -> LocationStreamState.Stopped
                        }
                    )
                }
            }
        }
    }

    private fun applyFrame(frame: AGChannelFrame) {
        when (frame) {
            is AGChannelFrame.Snapshot -> {
                val members = handleMembersWithSameName(frame.members).associateBy { it.id }
                    .toMutableMap()
                for (memberId in members.keys) {
                    val oldRecord = state.value.members?.get(memberId)?.lastLocationRecord ?: continue
                    val newRecord = members[memberId]!!.lastLocationRecord
                    if (newRecord == null || oldRecord.timestamp >= newRecord.timestamp) {
                        members[memberId] = members[memberId]!!.copy(lastLocationRecord = oldRecord)
                    }
                }
                _state.update {
                    it.copy(
                        members = members,
                        circles = frame.circles,
                        anonymousGroup = it.anonymousGroup?.let { group ->
                            members[group.memberId]?.let { me ->
                                group.copy(memberIsAGAdmin = me.isAGAdmin)
                            } ?: group
                        })
                }
            }

            is AGChannelFrame.LiveLocation -> {
                val update = frame.update
                val member = state.value.members?.get(update.agMemberId) ?: return
                _state.update {
                    it.copy(
                        members = it.members!! + (member.id to member.copy(lastLocationRecord = update.locationRecord))
                    )
                }
                if (state.value.isFollowingSelectedMember && update.agMemberId == state.value.selectedMemberId) {
                    _cameraPositionEvents.trySend(CameraTarget(update.locationRecord.coordinates))
                }
            }

            is AGChannelFrame.MemberJoined -> _state.update {
                val members = it.members ?: return@update it
                it.copy(
                    members = handleMembersWithSameName(members.values.toList() + frame.member)
                        .associateBy { member -> member.id })
            }

            is AGChannelFrame.MemberLeft -> _state.update {
                it.copy(
                    members = it.members?.minus(frame.memberId),
                    selectedMemberId = it.selectedMemberId.takeIf { id -> id != frame.memberId },
                    isFollowingSelectedMember = it.isFollowingSelectedMember && it.selectedMemberId != frame.memberId
                )
            }

            is AGChannelFrame.MemberUpdated -> _state.update {
                it.copy(
                    members = it.members?.let { members ->
                        members[frame.memberId]?.let { member ->
                            members + (member.id to member.copy(isAGAdmin = frame.isAGAdmin))
                        } ?: members
                    },
                    anonymousGroup = it.anonymousGroup?.let { group ->
                        if (group.memberId == frame.memberId) group.copy(memberIsAGAdmin = frame.isAGAdmin) else group
                    })
            }

            is AGChannelFrame.CircleUpserted -> _state.update {
                it.copy(
                    circles = if (it.circles.any { c -> c.id == frame.circle.id }) it.circles.map { c ->
                        if (c.id == frame.circle.id) frame.circle else c
                    } else it.circles + frame.circle
                )
            }

            is AGChannelFrame.CircleDeleted -> _state.update {
                it.copy(
                    circles = it.circles.filter { c -> c.id != frame.circleId },
                    selectedCircleId = it.selectedCircleId.takeIf { id -> id != frame.circleId }
                )
            }
        }
    }

    private fun listenForDeviceOrientation() {
        listenForDeviceOrientationJob?.cancel()
        listenForDeviceOrientationJob = viewModelScope.launch {
            deviceOrientationService.getOrientationUpdates().collect { deviceOrientation ->
                _state.update { it.copy(myDeviceOrientation = deviceOrientation) }
            }
        }
    }

    /**
     * Listens for the user's location, if called multiple times the previous job is canceled
     */
    @OptIn(ExperimentalTime::class)
    private fun listenForUserLocation() {
        listenForDeviceOrientation()
        listenForUserLocationJob?.cancel()
        listenForUserLocationJob = viewModelScope.launch {
            try {
                Log.d("", "Streaming my position")
                locationService.getLocationUpdates().collect { coordinates ->
                    if (state.value.myLocationRecordFromGPS == null) {
                        _cameraPositionEvents.trySend(CameraTarget(coordinates))
                    }
                    _state.update {
                        it.copy(
                            myLocationRecordFromGPS = LocationRecord(
                                coordinates = coordinates,
                                timestamp = Clock.System.now().toLocalDateTime(
                                    TimeZone.UTC
                                )
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
    private suspend fun getAndMoveToMyLocation(moveCamera: Boolean = true) {
        Log.d("", "Getting my location")
        try {
            val coordinates = locationService.getCurrentLocation()
            if (coordinates != null) {
                _state.update {
                    it.copy(
                        myLocationRecordFromGPS = LocationRecord(
                            coordinates = coordinates,
                            timestamp = Clock.System.now().toLocalDateTime(
                                timeZone = TimeZone.UTC
                            )
                        )
                    )
                }
                if (moveCamera) _cameraPositionEvents.trySend(CameraTarget(coordinates))
            } else if (moveCamera && state.value.myLocationRecordFromGPS != null) {
                _cameraPositionEvents.trySend(CameraTarget(state.value.myLocationRecordFromGPS!!.coordinates))
            }
        } catch (e: Exception) {
            if (moveCamera && state.value.myLocationRecordFromGPS != null) {
                _cameraPositionEvents.trySend(CameraTarget(state.value.myLocationRecordFromGPS!!.coordinates))
            }
            throw e
        }
        listenForUserLocation()
    }

    fun onTapMyLocation() {
        clearSelectedMember()
        moveToMyLocation()
    }

    private fun moveToMyLocation() {
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
            if (result.errorInfo.exception is CancellationException) {
                throw result.errorInfo.exception
            }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _snackbarEvents.trySend(
                SnackbarErrorMessage(
                    text = "Can't get local members", errorInfo = ErrorInfo.fromException(e)
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

    fun showDeleteAGDialog() {
        _state.update { it.copy(showDeleteAGDialog = true) }
    }

    fun hideDeleteAGDialog() {
        _state.update { it.copy(showDeleteAGDialog = false) }
    }

    fun showMakeAdminDialog(agMemberId: String) {
        _state.update { it.copy(makeAdminDialogMemberId = agMemberId) }
    }

    fun hideMakeAdminDialog() {
        _state.update { it.copy(makeAdminDialogMemberId = null) }
    }

    fun makeMemberAdmin() {
        val connectionSettingsId = connectionId ?: return
        val anonymousGroupInternalId = state.value.anonymousGroup?.internalId ?: return
        val agMemberId = state.value.makeAdminDialogMemberId ?: return
        viewModelScope.launch {
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                anonymousGroupService.promoteMember(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupInternalId = anonymousGroupInternalId,
                    agMemberId = agMemberId
                )
                _state.update {
                    it.copy(
                        showLoadingOverlay = false,
                        makeAdminDialogMemberId = null,
                        members = it.members?.let { members ->
                            members[agMemberId]?.let { member ->
                                members + (agMemberId to member.copy(isAGAdmin = true))
                            } ?: members
                        })
                }
            } catch (e: AGMemberNotAdminException) {
                _state.update {
                    it.copy(
                        showLoadingOverlay = false,
                        makeAdminDialogMemberId = null,
                        anonymousGroup = it.anonymousGroup?.copy(memberIsAGAdmin = false)
                    )
                }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "You are no longer an admin of this group",
                        errorInfo = ErrorInfo.fromException(e)
                    )
                )
            } catch (e: Exception) {
                _state.update { it.copy(showLoadingOverlay = false, makeAdminDialogMemberId = null) }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't make member admin", errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
        }
    }

    fun showLeaveAGDialog() {
        _state.update { it.copy(showLeaveAGDialog = true) }
    }

    fun hideLeaveAGDialog() {
        _state.update { it.copy(showLeaveAGDialog = false) }
    }

    fun leaveAnonymousGroup() {
        val connectionSettingsId = connectionId ?: return
        val anonymousGroupInternalId = state.value.anonymousGroup?.internalId ?: return
        viewModelScope.launch {
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                anonymousGroupService.leaveAnonymousGroup(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupInternalId = anonymousGroupInternalId
                )
                _state.update { it.copy(showLoadingOverlay = false, showLeaveAGDialog = false) }
            } catch (e: Exception) {
                _state.update { it.copy(showLoadingOverlay = false, showLeaveAGDialog = false) }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage(
                        text = "Can't leave anonymous group", errorInfo = ErrorInfo.fromException(e)
                    )
                )
            }
        }
    }

    fun deleteAnonymousGroup() {
        val connectionSettingsId = connectionId ?: return
        val anonymousGroupInternalId = state.value.anonymousGroup?.internalId ?: return
        viewModelScope.launch {
            _state.update { it.copy(showLoadingOverlay = true) }
            try {
                anonymousGroupService.deleteAnonymousGroup(
                    connectionSettingsId = connectionSettingsId,
                    anonymousGroupInternalId = anonymousGroupInternalId
                )
                _state.update { it.copy(showLoadingOverlay = false, showDeleteAGDialog = false) }
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
        _state.update { it.copy(dialogError = null) }
    }

    fun showErrorDialog(error: SnackbarErrorMessage) {
        _state.update { it.copy(dialogError = error) }
    }

    fun selectMember(agMemberId: String) {
        val anonymousGroup = state.value.anonymousGroup ?: return
        val member = state.value.members?.get(agMemberId) ?: return
        if (agMemberId == anonymousGroup.memberId) {
            _state.update { it.copy(selectedMemberId = agMemberId, isFollowingSelectedMember = false) }
            moveToMyLocation()
            return
        }
        val coordinates = member.lastLocationRecord?.coordinates
        _state.update {
            it.copy(selectedMemberId = agMemberId, isFollowingSelectedMember = coordinates != null)
        }
        coordinates?.let { _cameraPositionEvents.trySend(CameraTarget(it)) }
    }

    fun setFollowSelectedMember(follow: Boolean) {
        _state.update { it.copy(isFollowingSelectedMember = follow) }
        if (follow) {
            val selectedMemberId = state.value.selectedMemberId ?: return
            state.value.members?.get(selectedMemberId)?.lastLocationRecord?.let {
                _cameraPositionEvents.trySend(CameraTarget(it.coordinates))
            }
        }
    }

    fun onMapGesture() {
        _state.update { it.copy(isFollowingSelectedMember = false) }
    }

    fun toggleCircle(circleId: String) {
        _state.update {
            it.copy(selectedCircleId = if (it.selectedCircleId == circleId) null else circleId)
        }
    }

    fun clearSelectedMember() {
        _state.update { it.copy(selectedMemberId = null, isFollowingSelectedMember = false) }
    }

    private suspend fun getLocalCircles() {
        val internalId = state.value.anonymousGroup?.internalId ?: return
        _state.update { it.copy(circles = anonymousGroupService.listCircles(internalId)) }
    }
}
