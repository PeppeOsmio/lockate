package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates

data class Circle(
    val id: String,
    val name: String,
    val center: Coordinates,
    val radiusMeters: Double,
)

/** The circle [point] is inside whose centre is the closest to it, so overlapping circles pick one. */
fun List<Circle>.nearestContaining(point: Coordinates): Circle? =
    mapNotNull { circle ->
        val distance = point.distanceMetersTo(circle.center)
        if (distance <= circle.radiusMeters) circle to distance else null
    }.minByOrNull { it.second }?.first

enum class TransitionKind { Enter, Exit }

data class CircleTransition(val circleId: String, val kind: TransitionKind)
