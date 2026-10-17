package com.peppeosmio.lockate.srp;

import java.time.LocalDateTime;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@RegisterReflectionForBinding
public record SrpSession(String A, String b, String B, LocalDateTime createdAt) {}
