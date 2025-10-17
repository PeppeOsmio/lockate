package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.domain.LocationRecord
import kotlinx.datetime.LocalDateTime

data class AGMember(
    val internalId: Long,
    val id: String,
    val name: String,
    val createdAt: LocalDateTime,
    val lastLocationRecord: LocationRecord?
)
