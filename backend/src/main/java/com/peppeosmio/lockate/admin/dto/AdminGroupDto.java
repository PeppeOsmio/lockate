package com.peppeosmio.lockate.admin.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** {@code lastLocationAt} is the newest of the saved positions and the ones still in Redis. */
public record AdminGroupDto(
    UUID id,
    LocalDateTime createdAt,
    long memberCount,
    long adminCount,
    long onlineCount,
    long pushRegisteredCount,
    long circleCount,
    LocalDateTime lastLocationAt) {}
