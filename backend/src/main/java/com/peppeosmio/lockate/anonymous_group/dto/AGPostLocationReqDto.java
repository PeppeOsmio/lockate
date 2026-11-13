package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/** A position with the circle events it caused, which the server relays after it, in order. */
@RegisterReflectionForBinding
public record AGPostLocationReqDto(
    @Valid EncryptedDataDto encryptedCoordinates,
    @Valid EncryptedDataDto encryptedActivity,
    @Nullable List<@Valid EncryptedDataDto> circleEvents) {}
