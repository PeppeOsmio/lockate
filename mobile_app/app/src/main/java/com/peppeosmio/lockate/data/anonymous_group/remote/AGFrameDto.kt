package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

/** Mirrors the backend's AGFrameDto: only the fields of the frame's [type] are set. */
@Serializable
data class AGFrameDto(
    val type: String,
    val members: List<EncryptedAGMemberDto>? = null,
    val circles: List<CircleDto>? = null,
    val member: EncryptedAGMemberDto? = null,
    val memberId: String? = null,
    val circleEvent: CircleEventDto? = null,
    val isAGAdmin: Boolean? = null,
    val circle: CircleDto? = null,
    val circleId: String? = null,
) {
    companion object {
        const val SNAPSHOT = "snapshot"
        const val MEMBER_JOINED = "member.joined"
        const val MEMBER_LEFT = "member.left"
        const val MEMBER_UPDATED = "member.updated"
        const val CIRCLE_UPSERTED = "circle.upserted"
        const val CIRCLE_DELETED = "circle.deleted"
        const val CIRCLE_EVENT = "circle.event"
    }
}
