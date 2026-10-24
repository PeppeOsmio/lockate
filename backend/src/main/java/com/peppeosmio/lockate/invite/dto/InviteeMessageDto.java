package com.peppeosmio.lockate.invite.dto;

import jakarta.validation.constraints.NotBlank;

/** The invitee's J-PAKE rounds 1 and 2, base64 */
public record InviteeMessageDto(@NotBlank String round1, @NotBlank String round2) {}
