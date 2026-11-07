package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.crypto.EncryptedData

/**
 * What a member sends to the group. The position is always there, so that every record the server
 * stores is a position.
 */
data class MemberMessage(
    val coordinates: Coordinates,
    val activity: UserActivity,
)

data class EncryptedMemberMessage(
    val coordinates: EncryptedData,
    val activity: EncryptedData,
)
