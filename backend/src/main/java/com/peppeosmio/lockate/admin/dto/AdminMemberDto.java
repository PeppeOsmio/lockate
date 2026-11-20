package com.peppeosmio.lockate.admin.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminMemberDto(
    UUID id,
    LocalDateTime createdAt,
    boolean isAGAdmin,
    boolean online,
    boolean pushRegistered,
    LocalDateTime lastLocationAt) {}
