package com.peppeosmio.lockate.domain

import kotlinx.datetime.LocalDateTime

data class LocationRecord(
    val coordinates: Coordinates,
    val timestamp: LocalDateTime
)
