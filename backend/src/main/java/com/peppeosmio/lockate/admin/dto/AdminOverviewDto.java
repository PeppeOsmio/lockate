package com.peppeosmio.lockate.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The server at a glance. What the database knows is null when it is down, what Redis knows when
 * Redis is; {@code pushRelayUrl} is null when the server sends no push.
 */
public record AdminOverviewDto(
    String version,
    List<Integer> apis,
    LocalDateTime startedAt,
    boolean requireApiKey,
    String pushRelayUrl,
    long locationRetentionSeconds,
    long locationSaveIntervalSeconds,
    String locationRetentionCron,
    boolean databaseUp,
    boolean redisUp,
    Counts counts,
    Long onlineMembers,
    JobStatus locationSave,
    JobStatus locationRetention) {

  /** {@code inactiveGroups}: older than 30 days with no position saved in the last 30 */
  public record Counts(
      long groups,
      long members,
      long pushRegisteredMembers,
      long places,
      long groupsWithoutAdmin,
      long inactiveGroups,
      long savedLocations,
      long apiKeys) {}

  /** The last run (null when it never ran) and whether the next one is late */
  public record JobStatus(LocalDateTime lastRunAt, Long lastCount, boolean overdue) {}
}
