package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AGCreateReqDto(
        @NotNull EncryptedDataDto encryptedMemberName,
        @NotNull EncryptedDataDto encryptedGroupName,
        @NotBlank String memberPasswordSrpVerifier,
        @NotBlank String memberPasswordSrpSalt,
        @NotBlank String keySalt
) {
}

