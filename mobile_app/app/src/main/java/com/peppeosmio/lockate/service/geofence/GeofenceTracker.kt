package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationFix
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import kotlin.math.min

/**
 * Tells when we enter or leave the circles of a group, from our own fixes. Entering needs to get
 * [HYSTERESIS_MAX_METERS] (at most a fifth of the radius) inside the circle and leaving that far
 * outside, so a GPS jittering on the edge doesn't produce a burst of events, and fixes less
 * accurate than [MAX_ACCURACY_METERS] are ignored. The first fix, and a circle that is new, moved
 * or resized, take the state of our position without announcing anything, so nobody is told about
 * an arrival that happened hours ago. Called by the send loop and by the channel's frames, so every
 * method is synchronized.
 */
class GeofenceTracker {
    private val circles = mutableMapOf<String, Circle>()
    private var position: Coordinates? = null
    private val inside = mutableMapOf<String, Boolean>()

    @Synchronized
    fun replaceCircles(newCircles: List<Circle>) {
        circles.keys.filter { id -> newCircles.none { it.id == id } }.forEach { removeCircle(it) }
        newCircles.forEach { upsertCircle(it) }
    }

    @Synchronized
    fun upsertCircle(circle: Circle) {
        val old = circles.put(circle.id, circle)
        if (old == null || old.center != circle.center || old.radiusMeters != circle.radiusMeters) {
            position?.let { inside[circle.id] = circle.contains(it) }
        }
    }

    @Synchronized
    fun removeCircle(circleId: String) {
        circles.remove(circleId)
        inside.remove(circleId)
    }

    /** Makes the next fix seed the state silently, for when we haven't been tracked for a while. */
    @Synchronized
    fun forgetPosition() {
        position = null
        inside.clear()
    }

    @Synchronized
    fun update(fix: LocationFix): List<CircleTransition> {
        if (fix.accuracyMeters > MAX_ACCURACY_METERS) return emptyList()
        val coordinates = fix.coordinates
        if (position == null) {
            position = coordinates
            circles.values.forEach { inside[it.id] = it.contains(coordinates) }
            return emptyList()
        }
        position = coordinates
        return circles.values.mapNotNull { circle ->
            val distance = coordinates.distanceMetersTo(circle.center)
            val margin = min(HYSTERESIS_MAX_METERS, circle.radiusMeters * 0.2)
            val wasInside = inside.getValue(circle.id)
            val isInside = if (wasInside) {
                distance <= circle.radiusMeters + margin
            } else {
                distance < circle.radiusMeters - margin
            }
            if (isInside == wasInside) return@mapNotNull null
            inside[circle.id] = isInside
            CircleTransition(circle.id, if (isInside) TransitionKind.Enter else TransitionKind.Exit)
        }
    }

    private fun Circle.contains(position: Coordinates) =
        position.distanceMetersTo(center) <= radiusMeters

    companion object {
        private const val HYSTERESIS_MAX_METERS = 20.0
        const val MAX_ACCURACY_METERS = 50f
    }
}
