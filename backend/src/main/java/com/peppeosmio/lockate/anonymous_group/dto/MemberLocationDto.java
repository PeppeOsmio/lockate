package com.peppeosmio.lockate.anonymous_group.dto;

import java.util.UUID;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/** A member's location as relayed on the group's locations channel and sent on the SSE stream. */
@RegisterReflectionForBinding
public record MemberLocationDto(UUID memberId, LocationRecordDto record) {}
