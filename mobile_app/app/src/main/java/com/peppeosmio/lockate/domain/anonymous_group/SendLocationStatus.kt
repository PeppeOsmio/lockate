package com.peppeosmio.lockate.domain.anonymous_group

data class SendLocationStatus(
    val totalAGCount: Int,
    val activeAGCount: Int,
    val isLocationDisabled: Boolean,
    val isLocationUnavailable: Boolean
) {
    companion object {
        const val TEXT_NO_GROUPS = "No groups to send location to"
        const val TEXT_LOCATION_DISABLED = "Geolocation is disabled"
        const val TEXT_LOCATION_UNAVAILABLE = "Geolocation not available"
        const val TEXT_SERVER_UNREACHABLE = "Can't connect to the server"
        const val TEXT_SHARING_ONE = "Sharing location with 1 group"
        const val TEXT_SHARING_MANY_PREFIX = "Sharing location with "
        const val TEXT_SHARING_MANY_SUFFIX = " groups"
    }
}

fun SendLocationStatus.notificationText(): String = when {
    totalAGCount == 0 -> SendLocationStatus.TEXT_NO_GROUPS
    isLocationDisabled -> SendLocationStatus.TEXT_LOCATION_DISABLED
    isLocationUnavailable -> SendLocationStatus.TEXT_LOCATION_UNAVAILABLE
    activeAGCount == 0 -> SendLocationStatus.TEXT_SERVER_UNREACHABLE
    activeAGCount == 1 -> SendLocationStatus.TEXT_SHARING_ONE
    else -> "${SendLocationStatus.TEXT_SHARING_MANY_PREFIX}$activeAGCount${SendLocationStatus.TEXT_SHARING_MANY_SUFFIX}"
}
