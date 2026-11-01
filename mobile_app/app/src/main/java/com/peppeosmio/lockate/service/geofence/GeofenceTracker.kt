package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import kotlin.math.min

/**
 * Tells when this device enters or leaves the circles of a group. Entering needs to get
 * [HYSTERESIS_MAX_METERS] (at most a fifth of the radius) inside the circle and leaving that far
 * outside, so a GPS jittering on the edge doesn't produce a burst of events. A circle whose state
 * isn't known (new, moved, resized, or never seen) takes the state of the next fix without
 * announcing anything, so nobody is told about an arrival that happened hours ago.
 */
class GeofenceTracker {
    data class UpdateResult(
        val transitions: List<CircleTransition>,
        val changedStates: Map<String, Boolean>,
    )

    private val circles = mutableMapOf<String, Circle>()
    private val inside = mutableMapOf<String, Boolean>()

    fun states(): Map<String, Boolean> = inside.toMap()

    fun restoreStates(states: Map<String, Boolean>) {
        states.forEach { (id, isInside) -> if (id in circles) inside[id] = isInside }
    }

    fun replaceCircles(newCircles: List<Circle>) {
        circles.keys.filter { id -> newCircles.none { it.id == id } }.forEach { removeCircle(it) }
        newCircles.forEach { upsertCircle(it) }
    }

    fun upsertCircle(circle: Circle) {
        val old = circles.put(circle.id, circle)
        if (old != null && (old.center != circle.center || old.radiusMeters != circle.radiusMeters)) {
            inside.remove(circle.id)
        }
    }

    fun removeCircle(circleId: String) {
        circles.remove(circleId)
        inside.remove(circleId)
    }

    fun update(position: Coordinates): UpdateResult {
        val transitions = mutableListOf<CircleTransition>()
        val changed = mutableMapOf<String, Boolean>()
        circles.values.forEach { circle ->
            val distance = position.distanceMetersTo(circle.center)
            val margin = min(HYSTERESIS_MAX_METERS, circle.radiusMeters * 0.2)
            val wasInside = inside[circle.id]
            val isInside = when (wasInside) {
                null -> distance <= circle.radiusMeters
                true -> distance <= circle.radiusMeters + margin
                false -> distance < circle.radiusMeters - margin
            }
            if (isInside == wasInside) return@forEach
            inside[circle.id] = isInside
            changed[circle.id] = isInside
            if (wasInside != null) {
                transitions += CircleTransition(
                    circle.id, if (isInside) TransitionKind.Enter else TransitionKind.Exit
                )
            }
        }
        return UpdateResult(transitions, changed)
    }

    companion object {
        const val HYSTERESIS_MAX_METERS = 20.0
    }
}
