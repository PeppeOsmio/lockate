package com.peppeosmio.lockate.srp;

import com.peppeosmio.lockate.config.NativeReflection;
import java.time.LocalDateTime;

@NativeReflection
public record SrpSession(String A, String b, String B, LocalDateTime createdAt) {}
