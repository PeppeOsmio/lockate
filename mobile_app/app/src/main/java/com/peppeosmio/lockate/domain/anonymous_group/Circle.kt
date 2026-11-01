package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates

data class Circle(
    val id: String,
    val name: String,
    val center: Coordinates,
    val radiusMeters: Double,
)

enum class TransitionKind { Enter, Exit }

data class CircleTransition(val circleId: String, val kind: TransitionKind)
