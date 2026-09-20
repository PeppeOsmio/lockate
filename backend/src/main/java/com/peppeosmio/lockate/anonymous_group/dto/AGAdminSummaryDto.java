package com.peppeosmio.lockate.anonymous_group.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AGAdminSummaryDto(
    UUID id, LocalDateTime createdAt, long memberCount, LocalDateTime lastLocationAt) {}
