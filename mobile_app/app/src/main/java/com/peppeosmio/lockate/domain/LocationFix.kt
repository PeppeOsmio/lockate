package com.peppeosmio.lockate.domain

data class LocationFix(
    val coordinates: Coordinates,
    val accuracyMeters: Float,
    val atMillis: Long,
)
