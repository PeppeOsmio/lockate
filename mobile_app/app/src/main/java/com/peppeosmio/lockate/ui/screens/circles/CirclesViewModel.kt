package com.peppeosmio.lockate.ui.screens.circles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.anonymous_group.AGChannelFrame
import com.peppeosmio.lockate.domain.anonymous_group.AGLocationUpdate
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.utils.ErrorInfo
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

data class CirclesScreenState(
    val isLoading: Boolean = true,
    val groupName: String = "",
    val myMemberId: String = "",
    val circles: List<Circle> = emptyList(),
    // everyone, with their last position, to tell who is inside each circle
    val members: List<AGMember> = emptyList(),
    val mutedTargets: Set<String> = emptySet(),
    val query: String = "",
) {
    /** The circles whose name matches the search, by name */
    val shownCircles: List<Circle>
        get() =
            circles
                .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                .sortedBy { it.name.lowercase() }
}

class CirclesViewModel(private val anonymousGroupService: AnonymousGroupService) : ViewModel() {
    private val _state = MutableStateFlow(CirclesScreenState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>(Channel.BUFFERED)
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    private var group: AnonymousGroup? = null
    private var loadJob: Job? = null

    fun load(anonymousGroupInternalId: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val anonymousGroup =
                try {
                    anonymousGroupService.getAGByInternalId(anonymousGroupInternalId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false) }
                    _snackbarEvents.trySend(
                        SnackbarErrorMessage("Can't load the circles", ErrorInfo.fromException(e))
                    )
                    return@launch
                }
            group = anonymousGroup
            _state.update {
                it.copy(
                    isLoading = false,
                    groupName = anonymousGroup.name,
                    myMemberId = anonymousGroup.memberId,
                    circles = anonymousGroupService.listCircles(anonymousGroupInternalId),
                    members = anonymousGroupService.getLocalAGMembers(anonymousGroup),
                    mutedTargets = anonymousGroupService.listMutedTargets(anonymousGroupInternalId),
                )
            }
            // subscribed before the stream is held, so the snapshot of a new connection isn't
            // missed
            launch(start = CoroutineStart.UNDISPATCHED) {
                anonymousGroupService.groupFrames
                    .filter { it.anonymousGroupInternalId == anonymousGroupInternalId }
                    .collect { applyFrame(it.frame) }
            }
            anonymousGroupService.holdGroupStream(
                anonymousGroup,
                onState = {},
                onLocations = ::applyLocations,
            )
        }
    }

    private fun applyLocations(updates: List<AGLocationUpdate>) {
        val latest = updates.associateBy { it.agMemberId }
        _state.update {
            it.copy(
                members =
                    it.members.map { member ->
                        val update = latest[member.id] ?: return@map member
                        val known = member.lastLocationRecord
                        if (known != null && known.timestamp >= update.locationRecord.timestamp)
                            member
                        else member.copy(lastLocationRecord = update.locationRecord)
                    }
            )
        }
    }

    private fun applyFrame(frame: AGChannelFrame) {
        when (frame) {
            is AGChannelFrame.Snapshot ->
                _state.update {
                    it.copy(circles = frame.circles, members = frame.members)
                }

            is AGChannelFrame.CircleUpserted ->
                _state.update {
                    it.copy(
                        circles =
                            if (it.circles.any { c -> c.id == frame.circle.id })
                                it.circles.map { c ->
                                    if (c.id == frame.circle.id) frame.circle else c
                                }
                            else it.circles + frame.circle
                    )
                }

            is AGChannelFrame.CircleDeleted ->
                _state.update {
                    it.copy(circles = it.circles.filter { c -> c.id != frame.circleId })
                }

            is AGChannelFrame.MemberJoined ->
                _state.update {
                    it.copy(members = it.members + frame.member)
                }
            is AGChannelFrame.MemberLeft ->
                _state.update {
                    it.copy(members = it.members.filter { m -> m.id != frame.memberId })
                }

            is AGChannelFrame.CircleEvent,
            is AGChannelFrame.MemberUpdated,
            is AGChannelFrame.MemberPresence -> Unit
        }
    }

    fun setTargetMuted(targetId: String, muted: Boolean) {
        val internalId = group?.internalId ?: return
        viewModelScope.launch {
            anonymousGroupService.setTargetMuted(internalId, targetId, muted)
            _state.update {
                it.copy(
                    mutedTargets =
                        if (muted) it.mutedTargets + targetId else it.mutedTargets - targetId
                )
            }
        }
    }

    /** Notifies about every circle, or about none: one bell for all of them */
    fun setAllCirclesMuted(muted: Boolean) {
        val internalId = group?.internalId ?: return
        val circleIds = state.value.circles.map { it.id }
        viewModelScope.launch {
            for (circleId in circleIds) {
                anonymousGroupService.setTargetMuted(internalId, circleId, muted)
            }
            _state.update {
                it.copy(
                    mutedTargets =
                        if (muted) it.mutedTargets + circleIds
                        else it.mutedTargets - circleIds.toSet()
                )
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }
}
