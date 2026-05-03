package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnonymousGroupDto(
        UUID id, EncryptedDataDto encryptedName, LocalDateTime createdAt, String keySalt) {
}
