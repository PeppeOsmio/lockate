package com.peppeosmio.lockate.domain.anonymous_group

/**
 * What the foreground service's notification shows. Whether the posts get through isn't shown: a
 * post fails for a moment in a tunnel or on a network change.
 */
data class SendLocationStatus(val sharedAGCount: Int, val isLocationOff: Boolean) {
    companion object {
        const val TEXT_SHARING = "Sharing your location"
        const val TEXT_LOCATION_OFF = "Location is off"
    }
}

fun SendLocationStatus.notificationText(): String =
    if (isLocationOff) SendLocationStatus.TEXT_LOCATION_OFF else SendLocationStatus.TEXT_SHARING
