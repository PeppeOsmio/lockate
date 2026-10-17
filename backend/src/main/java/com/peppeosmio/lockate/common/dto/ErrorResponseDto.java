package com.peppeosmio.lockate.common.dto;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@RegisterReflectionForBinding
public record ErrorResponseDto(String error) {}
