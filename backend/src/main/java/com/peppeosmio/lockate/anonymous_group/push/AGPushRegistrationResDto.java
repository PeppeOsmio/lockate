package com.peppeosmio.lockate.anonymous_group.push;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/** {@code pushes}: whether this server sends pushes (it has a relay), so the app relies on them. */
@RegisterReflectionForBinding
public record AGPushRegistrationResDto(boolean pushes) {}
