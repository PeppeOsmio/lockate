package com.peppeosmio.lockate.invite.dto;

import jakarta.validation.constraints.NotBlank;

/** The inviter's J-PAKE round 2 and key confirmation tag, base64 */
public record InviterConfirmDto(@NotBlank String round2, @NotBlank String ta) {}
