package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import java.time.LocalDateTime;

public record LocationRecordDto(
    EncryptedDataDto encryptedCoordinates,
    EncryptedDataDto encryptedActivity,
    LocalDateTime timestamp) {}
