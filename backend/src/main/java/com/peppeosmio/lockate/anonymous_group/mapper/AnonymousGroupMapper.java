package com.peppeosmio.lockate.anonymous_group.mapper;

import com.peppeosmio.lockate.anonymous_group.dto.AnonymousGroupDto;
import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class AnonymousGroupMapper {
    public AnonymousGroupDto toDto(AnonymousGroupEntity entity) {
        Base64.Encoder encoder = Base64.getEncoder();
        return new AnonymousGroupDto(
                entity.getId(),
                new EncryptedDataDto(
                        encoder.encodeToString(entity.getNameCipher()),
                        encoder.encodeToString(entity.getNameIv())),
                entity.getCreatedAt(),
                encoder.encodeToString(entity.getKeySalt()));
    }
}
