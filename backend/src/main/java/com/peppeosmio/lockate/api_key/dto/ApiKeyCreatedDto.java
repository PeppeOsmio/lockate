package com.peppeosmio.lockate.api_key.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApiKeyCreatedDto(UUID id, String key, LocalDateTime createdAt) {}
