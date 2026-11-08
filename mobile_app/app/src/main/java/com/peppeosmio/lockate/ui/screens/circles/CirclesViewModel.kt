package com.peppeosmio.lockate.ui.screens.circles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.anonymous_group.AGChannelFrame
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
    val circles: List<Circle> = emptyList(),
    val members: List<AGMember> = emptyList(),
    val mutedTargets: Set<String> = emptySet(),
    val circleToDelete: Circle? = null,
    val isDeleting: Boolean = false,
)

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
            val anonymousGroup = try {
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
                    circles = anonymousGroupService.listCircles(anonymousGroupInternalId),
                    members = others(anonymousGroupService.getLocalAGMembers(anonymousGroup)),
                    mutedTargets = anonymousGroupService.listMutedTargets(anonymousGroupInternalId)
                )
            }
            // subscribed before the channel is held, so the snapshot of a new connection isn't missed
            launch(start = CoroutineStart.UNDISPATCHED) {
                anonymousGroupService.groupFrames
                    .filter { it.anonymousGroupInternalId == anonymousGroupInternalId }
                    .collect { applyFrame(it.frame) }
            }
            anonymousGroupService.holdChannel(anonymousGroup, send = false)
        }
    }

    // our own arrivals and departures never notify us
    private fun others(members: List<AGMember>) = members.filter { it.id != group?.memberId }

    private fun applyFrame(frame: AGChannelFrame) {
        when (frame) {
            is AGChannelFrame.Snapshot -> _state.update {
                it.copy(circles = frame.circles, members = others(frame.members))
            }

            is AGChannelFrame.CircleUpserted -> _state.update {
                it.copy(
                    circles = if (it.circles.any { c -> c.id == frame.circle.id }) it.circles.map { c ->
                        if (c.id == frame.circle.id) frame.circle else c
                    } else it.circles + frame.circle
                )
            }

            is AGChannelFrame.CircleDeleted -> _state.update {
                it.copy(circles = it.circles.filter { c -> c.id != frame.circleId })
            }

            is AGChannelFrame.MemberJoined -> _state.update {
                it.copy(members = others(it.members + frame.member))
            }
            is AGChannelFrame.MemberLeft -> _state.update {
                it.copy(members = it.members.filter { m -> m.id != frame.memberId })
            }

            is AGChannelFrame.CircleEvent, is AGChannelFrame.MemberUpdated -> Unit
        }
    }

    fun setTargetMuted(targetId: String, muted: Boolean) {
        val internalId = group?.internalId ?: return
        viewModelScope.launch {
            anonymousGroupService.setTargetMuted(internalId, targetId, muted)
            _state.update {
                it.copy(mutedTargets = if (muted) it.mutedTargets + targetId else it.mutedTargets - targetId)
            }
        }
    }

    fun showOnMap(circle: Circle) {
        group?.let { anonymousGroupService.showCircleOnMap(it, circle) }
    }

    fun askToDelete(circle: Circle) {
        _state.update { it.copy(circleToDelete = circle) }
    }

    fun cancelDelete() {
        _state.update { it.copy(circleToDelete = null) }
    }

    fun confirmDelete() {
        val anonymousGroup = group ?: return
        val circle = state.value.circleToDelete ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true) }
            try {
                anonymousGroupService.deleteCircle(anonymousGroup.connectionId, anonymousGroup, circle.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.trySend(
                    SnackbarErrorMessage("Can't delete the circle", ErrorInfo.fromException(e))
                )
            }
            _state.update { it.copy(isDeleting = false, circleToDelete = null) }
        }
    }
}
