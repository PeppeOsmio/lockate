package com.peppeosmio.lockate.anonymous_group.dto;

import java.util.Map;
import java.util.UUID;

public record AGGetMembersLastLocationResDto(Map<UUID, LocationRecordDto> locations) {
}
