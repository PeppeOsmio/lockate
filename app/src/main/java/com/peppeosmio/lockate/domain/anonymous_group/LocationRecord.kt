package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import kotlinx.datetime.LocalDateTime

data class LocationRecord(
    val coordinates: Coordinates,
    val timestamp: LocalDateTime
)
