package com.peppeosmio.lockate.api_key.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** What the admin sets on a key: a label to tell keys apart, and when it stops working (UTC) */
public record ApiKeyWriteDto(@Size(max = 60) String label, LocalDateTime expiresAt) {}
