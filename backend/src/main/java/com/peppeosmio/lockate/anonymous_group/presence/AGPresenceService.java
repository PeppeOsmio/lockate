package com.peppeosmio.lockate.anonymous_group.presence;

import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Tells the group when a member stops sending locations and when it starts again. Two keys per
 * member: {@code ag-presence}, refreshed by every location, whose expiry means offline, and {@code
 * ag-online}, which says we announced the member online. Every backend instance hears the expiry,
 * and only the one that deletes {@code ag-online} announces it. A lost expiry event is accepted:
 * {@code ag-online} then expires by itself, so the next location announces the member online again.
 */
@Service
public class AGPresenceService {
  static final Duration TIMEOUT = Duration.ofMinutes(5);
  static final Duration ONLINE_TTL = TIMEOUT.plusMinutes(1);
  private static final String PRESENCE_PREFIX = "ag-presence:";

  // A location may have come between the expiry and its event: then the member is still online
  static final String CLAIM_OFFLINE_SCRIPT =
      """
      if redis.call('EXISTS', KEYS[1]) == 1 then return false end
      return redis.call('GETDEL', KEYS[2])
      """;

  private final RedisService redisService;
  private final ObjectMapper objectMapper;

  public AGPresenceService(RedisService redisService, ObjectMapper objectMapper) {
    this.redisService = redisService;
    this.objectMapper = objectMapper;
    redisService.enableExpiredEvents();
    redisService.subscribePattern("__keyevent@*__:expired", this::onExpired);
  }

  private static String presenceKey(UUID anonymousGroupId, UUID memberId) {
    return PRESENCE_PREFIX + anonymousGroupId + ":" + memberId;
  }

  private static String onlineKey(UUID anonymousGroupId, UUID memberId) {
    return "ag-online:" + anonymousGroupId + ":" + memberId;
  }

  public void onLocation(UUID anonymousGroupId, UUID memberId) {
    // ag-presence first, so an expiry handled in between finds it back and doesn't claim
    redisService.saveValue(presenceKey(anonymousGroupId, memberId), "1", TIMEOUT);
    var previous =
        redisService.setAndGet(
            onlineKey(anonymousGroupId, memberId),
            LocalDateTime.now(ZoneOffset.UTC).toString(),
            ONLINE_TTL);
    if (previous.isEmpty()) {
      publish(anonymousGroupId, AGFrameDto.memberPresence(memberId, true, null));
    }
  }

  /** The member turned sharing off: the others learn it now rather than after the timeout. */
  public void pause(UUID anonymousGroupId, UUID memberId) {
    redisService.deleteValue(presenceKey(anonymousGroupId, memberId));
    redisService
        .getAndDelete(onlineKey(anonymousGroupId, memberId))
        .ifPresent(lastSeen -> announceOffline(anonymousGroupId, memberId, lastSeen));
  }

  /** The member left the group, which member.left already tells. */
  public void forget(UUID anonymousGroupId, UUID memberId) {
    redisService.deleteValue(presenceKey(anonymousGroupId, memberId));
    redisService.deleteValue(onlineKey(anonymousGroupId, memberId));
  }

  private void onExpired(String key) {
    if (!key.startsWith(PRESENCE_PREFIX)) {
      return;
    }
    var ids = key.substring(PRESENCE_PREFIX.length()).split(":");
    var anonymousGroupId = UUID.fromString(ids[0]);
    var memberId = UUID.fromString(ids[1]);
    redisService
        .runScript(CLAIM_OFFLINE_SCRIPT, List.of(key, onlineKey(anonymousGroupId, memberId)))
        .ifPresent(lastSeen -> announceOffline(anonymousGroupId, memberId, lastSeen));
  }

  private void announceOffline(UUID anonymousGroupId, UUID memberId, String lastSeen) {
    publish(
        anonymousGroupId,
        AGFrameDto.memberPresence(memberId, false, LocalDateTime.parse(lastSeen)));
  }

  private void publish(UUID anonymousGroupId, AGFrameDto frame) {
    redisService.publish(
        AnonymousGroupService.getRedisAGEventsChannel(anonymousGroupId),
        objectMapper.writeValueAsString(frame));
  }
}
