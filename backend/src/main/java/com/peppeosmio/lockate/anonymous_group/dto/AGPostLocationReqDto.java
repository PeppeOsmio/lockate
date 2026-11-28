package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/**
 * A position with the place events it caused, which the server relays after it, in order. {@code
 * placesVersion} is the version of the places the events were computed on: events on any other
 * version are refused, so none goes out about places that changed meanwhile.
 */
@RegisterReflectionForBinding
public record AGPostLocationReqDto(
    @Valid EncryptedDataDto encryptedCoordinates,
    @Valid EncryptedDataDto encryptedActivity,
    @Nullable List<@Valid EncryptedDataDto> placeEvents,
    @Nullable Long placesVersion) {}
