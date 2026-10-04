package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.config.NativeReflection;

@NativeReflection
public record AGLocationSaveReqDto(EncryptedDataDto encryptedLocation) {}
