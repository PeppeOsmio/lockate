package com.peppeosmio.lockate.anonymous_group.dto;

import java.util.UUID;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@RegisterReflectionForBinding
public record LocationUpdateDto(LocationRecordDto location, UUID memberId) {}
