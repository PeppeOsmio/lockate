package com.peppeosmio.lockate.anonymous_group.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AGGroupSummaryProjection {
  UUID getGroupId();

  long getMemberCount();

  LocalDateTime getLastLocationAt();
}
