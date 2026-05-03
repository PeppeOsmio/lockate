package com.peppeosmio.lockate.anonymous_group.mapper;

import com.peppeosmio.lockate.anonymous_group.dto.LocationRecordDto;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import org.springframework.stereotype.Component;

@Component
public class LocationRecordMapper {
    public LocationRecordDto toDto(AGMemberLocationEntity entity) {
        return new LocationRecordDto(
                EncryptedDataDto.fromEncryptedString(
                        new EncryptedString(
                                entity.getCoordinatesCipher(), entity.getCoordinatesIv())),
                entity.getTimestamp());
    }
}
