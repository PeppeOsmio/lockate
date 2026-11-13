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
 * an arrival that happened hours ago.
 *
 * The transitions are computed against the state the server confirmed, not the last one seen: they
 * are returned by every [update] until [confirm] is called with them, so a post that failed is
 * repeated by the next one, an entry and an exit both made meanwhile cancel out, and one waiting
 * for more than [MAX_PENDING_MILLIS] is dropped, since the receivers would take it as just
 * happened. Called by the send loop and by the channel's frames, so every method is synchronized.
 */
class GeofenceTracker {
    private val circles = mutableMapOf<String, Circle>()
    private var position: Coordinates? = null
    private val inside = mutableMapOf<String, Boolean>()
    private val confirmed = mutableMapOf<String, Boolean>()
    private val divergedAt = mutableMapOf<String, Long>()

    @Synchronized
    fun replaceCircles(newCircles: List<Circle>) {
        circles.keys.filter { id -> newCircles.none { it.id == id } }.forEach { removeCircle(it) }
        newCircles.forEach { upsertCircle(it) }
    }

    @Synchronized
    fun upsertCircle(circle: Circle) {
        val old = circles.put(circle.id, circle)
        if (old == null || old.center != circle.center || old.radiusMeters != circle.radiusMeters) {
            position?.let { seed(circle.id, circle.contains(it)) }
        }
    }

    @Synchronized
    fun removeCircle(circleId: String) {
        circles.remove(circleId)
        inside.remove(circleId)
        confirmed.remove(circleId)
        divergedAt.remove(circleId)
    }

    /** Makes the next fix seed the state silently, for when we haven't been tracked for a while. */
    @Synchronized
    fun forgetPosition() {
        position = null
        inside.clear()
        confirmed.clear()
        divergedAt.clear()
    }

    /** The transitions the server hasn't confirmed yet, after taking [fix] into account. */
    @Synchronized
    fun update(fix: LocationFix, nowMillis: Long): List<CircleTransition> {
        if (fix.accuracyMeters <= MAX_ACCURACY_METERS) {
            see(fix.coordinates, nowMillis)
        }
        return pending(nowMillis)
    }

    /** The server got [transitions]: they are no longer returned. */
    @Synchronized
    fun confirm(transitions: List<CircleTransition>, nowMillis: Long) {
        transitions
            .filter { it.circleId in circles }
            .forEach {
                confirmed[it.circleId] = it.kind == TransitionKind.Enter
                track(it.circleId, nowMillis)
            }
    }

    private fun see(coordinates: Coordinates, nowMillis: Long) {
        if (position == null) {
            position = coordinates
            circles.values.forEach { seed(it.id, it.contains(coordinates)) }
            return
        }
        position = coordinates
        circles.values.forEach { circle ->
            val distance = coordinates.distanceMetersTo(circle.center)
            val margin = min(HYSTERESIS_MAX_METERS, circle.radiusMeters * 0.2)
            val wasInside = inside.getValue(circle.id)
            val isInside =
                if (wasInside) {
                    distance <= circle.radiusMeters + margin
                } else {
                    distance < circle.radiusMeters - margin
                }
            if (isInside != wasInside) {
                inside[circle.id] = isInside
                track(circle.id, nowMillis)
            }
        }
    }

    private fun pending(nowMillis: Long): List<CircleTransition> {
        divergedAt.entries.removeAll { (id, since) ->
            (nowMillis - since >= MAX_PENDING_MILLIS).also { stale ->
                if (stale) confirmed[id] = inside.getValue(id)
            }
        }
        return divergedAt.keys.map { id ->
            CircleTransition(
                id,
                if (inside.getValue(id)) TransitionKind.Enter else TransitionKind.Exit,
            )
        }
    }

    private fun seed(circleId: String, isInside: Boolean) {
        inside[circleId] = isInside
        confirmed[circleId] = isInside
        divergedAt.remove(circleId)
    }

    private fun track(circleId: String, nowMillis: Long) {
        if (inside[circleId] == confirmed[circleId]) {
            divergedAt.remove(circleId)
        } else {
            divergedAt.putIfAbsent(circleId, nowMillis)
        }
    }

    private fun Circle.contains(position: Coordinates) =
        position.distanceMetersTo(center) <= radiusMeters

    companion object {
        private const val HYSTERESIS_MAX_METERS = 20.0
        const val MAX_ACCURACY_METERS = 50f
        const val MAX_PENDING_MILLIS = 5 * 60_000L
    }
}
