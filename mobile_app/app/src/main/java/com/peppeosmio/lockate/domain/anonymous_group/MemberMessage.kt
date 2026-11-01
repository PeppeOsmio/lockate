package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.UserActivity

/**
 * What a member sends to the group. The position is always there, so that every record the server
 * stores is a position; transitions are only meaningful when the message is received live.
 */
data class MemberMessage(
    val coordinates: Coordinates,
    val sentAtMillis: Long,
    val transitions: List<CircleTransition>,
    val activity: UserActivity,
)
