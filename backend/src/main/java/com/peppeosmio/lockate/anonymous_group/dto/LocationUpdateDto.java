package com.peppeosmio.lockate.anonymous_group.dto;

import java.util.UUID;

public record LocationUpdateDto(LocationRecordDto location, UUID memberId) {}
