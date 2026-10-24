package com.peppeosmio.lockate.invite.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;

/** What the inviter has posted so far, for the invitee's polling. Missing parts are null. */
public record InviterMessageDto(String round2, String ta, EncryptedDataDto payload) {}
