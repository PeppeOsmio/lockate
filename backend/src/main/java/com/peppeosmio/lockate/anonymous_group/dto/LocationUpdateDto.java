package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.config.NativeReflection;
import java.util.UUID;

@NativeReflection
public record LocationUpdateDto(LocationRecordDto location, UUID memberId) {}
