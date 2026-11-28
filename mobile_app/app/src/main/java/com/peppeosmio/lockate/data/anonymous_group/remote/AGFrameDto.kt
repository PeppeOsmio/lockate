package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/** Mirrors the backend's AGFrameDto: only the fields of the frame's [type] are set. */
@Serializable
data class AGFrameDto(
    val type: String,
    val members: List<EncryptedAGMemberDto>? = null,
    val places: List<PlaceDto>? = null,
    val member: EncryptedAGMemberDto? = null,
    val memberId: String? = null,
    val placeEvent: PlaceEventDto? = null,
    val isAGAdmin: Boolean? = null,
    val place: PlaceDto? = null,
    val placeId: String? = null,
    val online: Boolean? = null,
    val lastSeen: LocalDateTime? = null,
) {
    companion object {
        const val SNAPSHOT = "snapshot"
        const val MEMBER_JOINED = "member.joined"
        const val MEMBER_LEFT = "member.left"
        const val MEMBER_UPDATED = "member.updated"
        const val PLACE_UPSERTED = "place.upserted"
        const val PLACE_DELETED = "place.deleted"
        const val PLACE_EVENT = "place.event"
        const val MEMBER_PRESENCE = "member.presence"
        // only in pushes: a socket is closed with 4404 instead
        const val GROUP_DELETED = "group.deleted"
    }
}
