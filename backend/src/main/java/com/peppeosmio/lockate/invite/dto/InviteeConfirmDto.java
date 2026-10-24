package com.peppeosmio.lockate.invite.dto;

import jakarta.validation.constraints.NotBlank;

/** The invitee's key confirmation tag, base64 */
public record InviteeConfirmDto(@NotBlank String tb) {}
