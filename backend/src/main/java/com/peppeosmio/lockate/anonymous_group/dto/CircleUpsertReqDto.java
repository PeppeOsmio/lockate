package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CircleUpsertReqDto(@NotNull @Valid EncryptedDataDto encryptedData) {}
