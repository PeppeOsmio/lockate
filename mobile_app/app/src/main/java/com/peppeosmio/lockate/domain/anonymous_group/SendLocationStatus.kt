package com.peppeosmio.lockate.domain.anonymous_group

data class SendLocationStatus(
    val totalAGCount: Int, val activeAGCount: Int, val isLocationDisabled: Boolean
)
