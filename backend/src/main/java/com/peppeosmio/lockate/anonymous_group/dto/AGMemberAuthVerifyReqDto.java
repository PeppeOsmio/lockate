package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AGMemberAuthVerifyReqDto(@NotNull EncryptedDataDto encryptedMemberName,
                                       @NotBlank String srpSessionId,
                                       @NotBlank String M1) {
}
