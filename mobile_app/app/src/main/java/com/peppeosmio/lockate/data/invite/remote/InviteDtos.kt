package com.peppeosmio.lockate.data.invite.remote

import com.peppeosmio.lockate.data.anonymous_group.remote.EncryptedDataDto
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class InviteCreateReqDto(val sid: String, val round1: String)

@Serializable
data class InviteCreateResDto(
    val inviteId: String, val nameplate: Int, val joinToken: String, val expiresAt: LocalDateTime
)

@Serializable
data class InviterHelloDto(val sid: String, val round1: String)

@Serializable
data class InviteeMessageDto(val round1: String, val round2: String)

@Serializable
data class InviteClaimDto(val claimToken: String)

/** What the invitee has posted so far; the parts it hasn't are null */
@Serializable
data class InviteeProgressDto(
    val round1: String? = null, val round2: String? = null, val tb: String? = null
)

@Serializable
data class InviterConfirmDto(val round2: String, val ta: String)

@Serializable
data class InviteeConfirmDto(val tb: String)

@Serializable
data class InviterFinalDto(val payload: EncryptedDataDto)

/** What the inviter has posted so far; the parts it hasn't are null */
@Serializable
data class InviterMessageDto(
    val round2: String? = null, val ta: String? = null, val payload: EncryptedDataDto? = null
)
