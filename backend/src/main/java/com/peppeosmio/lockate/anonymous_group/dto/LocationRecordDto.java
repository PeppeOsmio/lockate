package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;

import java.time.LocalDateTime;

public record LocationRecordDto(
         EncryptedDataDto encryptedCoordinates, LocalDateTime timestamp) {
    public static LocationRecordDto fromEntity(AGMemberLocationEntity entity) {
        return new LocationRecordDto(
                EncryptedDataDto.fromEncryptedString(
                        new EncryptedString(
                                entity.getCoordinatesCipher(), entity.getCoordinatesIv())),
                entity.getTimestamp());
    }
}
