package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import java.time.LocalDateTime;

public record CircleEventDto(EncryptedDataDto encryptedEvent, LocalDateTime timestamp) {}
