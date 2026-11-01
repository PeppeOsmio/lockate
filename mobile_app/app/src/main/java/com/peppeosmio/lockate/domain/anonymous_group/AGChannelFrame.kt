package com.peppeosmio.lockate.domain.anonymous_group

/** What happens in a group, decrypted. The channel has already applied it to the local database. */
sealed interface AGChannelFrame {
    data class Snapshot(val members: List<AGMember>, val circles: List<Circle>) : AGChannelFrame
    data class LiveLocation(val update: AGLocationUpdate) : AGChannelFrame
    data class MemberJoined(val member: AGMember) : AGChannelFrame
    data class MemberLeft(val memberId: String) : AGChannelFrame
    data class MemberUpdated(val memberId: String, val isAGAdmin: Boolean) : AGChannelFrame
    data class CircleUpserted(val circle: Circle) : AGChannelFrame
    data class CircleDeleted(val circleId: String) : AGChannelFrame
}

data class GroupFrame(
    val anonymousGroupInternalId: Long,
    val anonymousGroupId: String,
    val frame: AGChannelFrame
)

enum class ChannelState { Connecting, Connected, Reconnecting, Stopped }

data class ChannelStatus(
    val state: ChannelState = ChannelState.Connecting,
    val isLocationDisabled: Boolean = false,
    val isLocationUnavailable: Boolean = false,
)
