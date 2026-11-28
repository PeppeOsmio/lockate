package com.peppeosmio.lockate.domain.anonymous_group

import kotlinx.datetime.LocalDateTime

/** What happens in a group, decrypted. The channel has already applied it to the local database. */
sealed interface AGChannelFrame {
    data class Snapshot(val members: List<AGMember>, val places: List<Place>) : AGChannelFrame

    data class PlaceEvent(
        val memberId: String,
        val transition: PlaceTransition,
        val timestamp: LocalDateTime,
    ) : AGChannelFrame

    data class MemberJoined(val member: AGMember) : AGChannelFrame

    /** The member stopped or started sending its location; [lastSeen] only when it stopped. */
    data class MemberPresence(
        val memberId: String,
        val online: Boolean,
        val lastSeen: LocalDateTime?,
    ) : AGChannelFrame

    data class MemberLeft(val memberId: String) : AGChannelFrame

    data class MemberUpdated(val memberId: String, val isAGAdmin: Boolean) : AGChannelFrame

    data class PlaceUpserted(val place: Place) : AGChannelFrame

    data class PlaceDeleted(val placeId: String) : AGChannelFrame
}

data class GroupFrame(
    val anonymousGroupInternalId: Long,
    val anonymousGroupId: String,
    val frame: AGChannelFrame,
)

enum class ChannelState {
    Connecting,
    Connected,
    Reconnecting,
    Stopped,
}

data class ChannelStatus(
    val isLocationDisabled: Boolean = false,
    val isLocationUnavailable: Boolean = false,
)
