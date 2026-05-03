package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;

import java.time.LocalDateTime;
import java.util.List;

public record AGDetailsResDto(String id, EncryptedDataDto encryptedName,
                              LocalDateTime createdAt, List<AGMemberDto> members) {
}
