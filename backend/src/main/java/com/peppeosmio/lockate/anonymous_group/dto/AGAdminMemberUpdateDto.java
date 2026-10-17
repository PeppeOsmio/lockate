package com.peppeosmio.lockate.anonymous_group.dto;

import jakarta.validation.constraints.NotNull;

public record AGAdminMemberUpdateDto(@NotNull Boolean isAGAdmin) {}
