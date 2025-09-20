package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.service.location.Location
import kotlinx.datetime.LocalDateTime

data class AGLocation(
    val coordinates: Location,
    val timestamp: LocalDateTime
)
