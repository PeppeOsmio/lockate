package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public record AGMemberDto(
        UUID id,
        EncryptedDataDto encryptedName,
        LocalDateTime createdAt,
        boolean isAGAdmin,
        Optional<LocationRecordDto> encryptedLastLocationRecord) {
}
