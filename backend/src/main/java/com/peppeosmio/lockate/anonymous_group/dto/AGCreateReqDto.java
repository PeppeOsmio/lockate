package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** The ids are chosen by the client: they are part of the AAD of the encrypted names. */
public record AGCreateReqDto(
    @NotNull UUID groupId,
    @NotNull UUID memberId,
    @NotNull @Valid EncryptedDataDto encryptedMemberName,
    @NotNull @Valid EncryptedDataDto encryptedGroupName) {}
