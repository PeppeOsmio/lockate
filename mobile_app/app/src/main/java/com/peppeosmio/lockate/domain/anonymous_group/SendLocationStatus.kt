package com.peppeosmio.lockate.domain.anonymous_group

data class SendLocationStatus(
    val totalAGCount: Int,
    val activeAGCount: Int,
    val isLocationDisabled: Boolean,
    val isLocationUnavailable: Boolean
)

fun SendLocationStatus.notificationText(): String = when {
    totalAGCount == 0 -> "No groups to send location to"
    isLocationDisabled -> "Geolocation is disabled"
    isLocationUnavailable -> "Geolocation not available"
    activeAGCount == 0 -> "Connecting..."
    activeAGCount == 1 -> "Sharing location with 1 group"
    else -> "Sharing location with $activeAGCount groups"
}
