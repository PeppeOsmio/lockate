package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** The member id is chosen by the client: it is part of the AAD of the encrypted name. */
public record AGJoinReqDto(
    @NotBlank String joinToken,
    @NotNull UUID memberId,
    @NotNull @Valid EncryptedDataDto encryptedMemberName) {}
