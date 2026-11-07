package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import java.time.LocalDateTime;
import java.util.UUID;

public record CircleDto(
    UUID id,
    EncryptedDataDto encryptedCoordinates,
    EncryptedDataDto encryptedRadius,
    EncryptedDataDto encryptedName,
    LocalDateTime updatedAt) {}
