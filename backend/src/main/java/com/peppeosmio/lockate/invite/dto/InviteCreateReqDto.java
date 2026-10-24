package com.peppeosmio.lockate.invite.dto;

import jakarta.validation.constraints.NotBlank;

/** The inviter's session id and J-PAKE round 1, base64 */
public record InviteCreateReqDto(@NotBlank String sid, @NotBlank String round1) {}
