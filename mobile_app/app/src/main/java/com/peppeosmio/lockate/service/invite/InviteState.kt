package com.peppeosmio.lockate.service.invite

import kotlin.time.Instant

sealed interface InviteState {
    data object Creating : InviteState

    data class WaitingForInvitee(val code: InviteCode, val expiresAt: Instant) : InviteState

    /** The invitee answered and the two phones are checking that they used the same code */
    data object Verifying : InviteState

    data object Sending : InviteState

    /** The invitee has the group; it joins on its own with the token it received */
    data object Done : InviteState

    /** The invitee typed different words, or someone else tried this code first */
    data object WrongCode : InviteState

    data object Expired : InviteState

    data class Failed(val exception: Throwable) : InviteState
}
