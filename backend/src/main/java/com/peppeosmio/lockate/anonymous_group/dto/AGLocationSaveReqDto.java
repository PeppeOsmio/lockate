package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@RegisterReflectionForBinding
public record AGLocationSaveReqDto(EncryptedDataDto encryptedLocation) {}
