package com.peppeosmio.lockate.domain

import kotlinx.datetime.LocalDateTime

data class LocationRecord(
    val id: String,
    val coordinates: Coordinates,
    val timestamp: LocalDateTime
)
