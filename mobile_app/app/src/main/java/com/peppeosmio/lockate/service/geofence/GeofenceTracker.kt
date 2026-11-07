package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import kotlinx.datetime.LocalDateTime
import kotlin.math.min

/**
 * Tells when the other members of a group enter or leave its circles, from the positions they
 * send. Entering needs to get [HYSTERESIS_MAX_METERS] (at most a fifth of the radius) inside the
 * circle and leaving that far outside, so a GPS jittering on the edge doesn't produce a burst of
 * events. A member seen for the first time, and a circle that is new, moved or resized, take the
 * state of the member's position without announcing anything, so nobody is told about an arrival
 * that happened hours ago. Positions are ordered by the server's timestamp, so a stored record
 * older than a live position (from the snapshot of a reconnect) can't rewind a member.
 */
class GeofenceTracker {
    private class MemberState(
        var position: Coordinates,
        var at: LocalDateTime,
        val inside: MutableMap<String, Boolean>,
    )

    private val circles = mutableMapOf<String, Circle>()
    private val members = mutableMapOf<String, MemberState>()

    fun replaceCircles(newCircles: List<Circle>) {
        circles.keys.filter { id -> newCircles.none { it.id == id } }.forEach { removeCircle(it) }
        newCircles.forEach { upsertCircle(it) }
    }

    fun upsertCircle(circle: Circle) {
        val old = circles.put(circle.id, circle)
        if (old == null || old.center != circle.center || old.radiusMeters != circle.radiusMeters) {
            members.values.forEach { it.inside[circle.id] = circle.contains(it.position) }
        }
    }

    fun removeCircle(circleId: String) {
        circles.remove(circleId)
        members.values.forEach { it.inside.remove(circleId) }
    }

    fun seedMember(memberId: String, position: Coordinates, at: LocalDateTime) {
        val known = members[memberId]
        if (known != null && at <= known.at) return
        members[memberId] = MemberState(
            position = position,
            at = at,
            inside = circles.values.associateTo(mutableMapOf()) { it.id to it.contains(position) }
        )
    }

    fun update(memberId: String, position: Coordinates, at: LocalDateTime): List<CircleTransition> {
        val state = members[memberId]
        if (state == null) {
            seedMember(memberId, position, at)
            return emptyList()
        }
        if (at < state.at) return emptyList()
        state.position = position
        state.at = at
        return circles.values.mapNotNull { circle ->
            val distance = position.distanceMetersTo(circle.center)
            val margin = min(HYSTERESIS_MAX_METERS, circle.radiusMeters * 0.2)
            val wasInside = state.inside.getValue(circle.id)
            val isInside = if (wasInside) {
                distance <= circle.radiusMeters + margin
            } else {
                distance < circle.radiusMeters - margin
            }
            if (isInside == wasInside) return@mapNotNull null
            state.inside[circle.id] = isInside
            CircleTransition(circle.id, if (isInside) TransitionKind.Enter else TransitionKind.Exit)
        }
    }

    fun retainMembers(memberIds: Set<String>) {
        members.keys.retainAll(memberIds)
    }

    fun removeMember(memberId: String) {
        members.remove(memberId)
    }

    private fun Circle.contains(position: Coordinates) =
        position.distanceMetersTo(center) <= radiusMeters

    companion object {
        private const val HYSTERESIS_MAX_METERS = 20.0
    }
}
