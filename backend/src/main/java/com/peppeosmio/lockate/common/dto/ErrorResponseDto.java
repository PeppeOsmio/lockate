package com.peppeosmio.lockate.common.dto;

import com.peppeosmio.lockate.config.NativeReflection;

@NativeReflection
public record ErrorResponseDto(String error) {}
