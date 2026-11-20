package com.peppeosmio.lockate.api_key.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** {@code lastValidated} is at most 10 minutes older than the key's last use */
public record ApiKeyDto(
    UUID id,
    String label,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    LocalDateTime lastValidated) {}
