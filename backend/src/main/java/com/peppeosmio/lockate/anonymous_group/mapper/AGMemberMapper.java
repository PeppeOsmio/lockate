package com.peppeosmio.lockate.anonymous_group.mapper;

import com.peppeosmio.lockate.anonymous_group.dto.AGMemberDto;
import com.peppeosmio.lockate.anonymous_group.dto.LocationRecordDto;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;

import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Optional;

@Component
public class AGMemberMapper {
    private final LocationRecordMapper locationRecordMapper;

    public AGMemberMapper(LocationRecordMapper locationRecordMapper) {
        this.locationRecordMapper = locationRecordMapper;
    }

    public AGMemberDto toDto(AGMemberEntity entity) {
        var encoder = Base64.getEncoder();
        LocationRecordDto lastLocationRecord = null;
        if (entity.getLastLocation() != null) {
            lastLocationRecord = locationRecordMapper.toDto(entity.getLastLocation());
        }
        return new AGMemberDto(
                entity.getId(),
                new EncryptedDataDto(
                        encoder.encodeToString(entity.getNameCipher()),
                        encoder.encodeToString(entity.getNameIv())),
                entity.getCreatedAt(),
                entity.isAGAdmin(),
                Optional.ofNullable(lastLocationRecord));
    }
}
