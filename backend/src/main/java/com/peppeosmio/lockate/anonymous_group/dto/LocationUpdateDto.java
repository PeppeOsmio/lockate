package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import java.util.UUID;

public record LocationUpdateDto(LocationRecordDto location, UUID memberId){
}