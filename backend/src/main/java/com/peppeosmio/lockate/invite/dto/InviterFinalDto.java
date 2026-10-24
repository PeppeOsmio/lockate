package com.peppeosmio.lockate.invite.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.validation.constraints.NotNull;

/** The group credentials, encrypted for the invitee */
public record InviterFinalDto(@NotNull EncryptedDataDto payload) {}
