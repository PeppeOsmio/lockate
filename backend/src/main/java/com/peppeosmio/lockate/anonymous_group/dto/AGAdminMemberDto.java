package com.peppeosmio.lockate.anonymous_group.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AGAdminMemberDto(
    UUID id, LocalDateTime createdAt, boolean isAGAdmin, LocalDateTime lastLocationAt) {}
