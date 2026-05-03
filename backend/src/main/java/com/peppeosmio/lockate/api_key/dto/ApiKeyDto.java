package com.peppeosmio.lockate.api_key.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApiKeyDto(UUID key, LocalDateTime createdAt) {
}
