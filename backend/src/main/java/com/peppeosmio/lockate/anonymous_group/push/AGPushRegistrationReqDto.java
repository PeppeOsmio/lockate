package com.peppeosmio.lockate.anonymous_group.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/** The FCM token of the member's phone, and the id and AES key its pushes are sealed with. */
@RegisterReflectionForBinding
public record AGPushRegistrationReqDto(
    @NotBlank @Size(max = AGPushService.MAX_TOKEN_LENGTH) String fcmToken,
    @NotBlank String registrationId,
    @NotBlank String key) {}
