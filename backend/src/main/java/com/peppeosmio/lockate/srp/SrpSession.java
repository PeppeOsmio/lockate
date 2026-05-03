package com.peppeosmio.lockate.srp;

import java.time.LocalDateTime;

public record SrpSession(String A, String b, String B, LocalDateTime createdAt) {
}
