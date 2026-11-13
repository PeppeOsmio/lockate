package com.peppeosmio.lockate.anonymous_group.service;

import com.peppeosmio.lockate.anonymous_group.dto.AGCircleEventReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGMemberMessageReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGPostLocationReqDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import com.peppeosmio.lockate.redis.RedisService;
import com.peppeosmio.lockate.utils.TTLMap;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * What a member sends over REST: its positions, each with the circle events it caused, and the
 * pause when it stops sharing.
 */
@Service
public class AGMemberMessageService {
  static final int MAX_CIRCLE_EVENTS = 16;
  // the app posts at most one fix every 1.5 s, 40 a minute
  static final int MAX_LOCATIONS_PER_MINUTE = 120;

  private final AnonymousGroupService anonymousGroupService;
  private final AGPresenceService presenceService;
  private final RedisService redisService;
  private final TTLMap<UUID, LocalDateTime> lastSavedLocations =
      new TTLMap<>(Duration.ofMinutes(5));

  public AGMemberMessageService(
      AnonymousGroupService anonymousGroupService,
      AGPresenceService presenceService,
      RedisService redisService) {
    this.anonymousGroupService = anonymousGroupService;
    this.presenceService = presenceService;
    this.redisService = redisService;
  }

  /** Everything is checked before anything is relayed, so a bad request relays nothing. */
  public void postLocation(
      UUID anonymousGroupId, AGMemberAuthentication authentication, AGPostLocationReqDto dto)
      throws TooManyRequestsException,
          InvalidEncryptedDataException,
          AGNotFoundException,
          UnauthorizedException {
    var memberId = authentication.getId();
    if (redisService.increment("rate:location:" + memberId, Duration.ofMinutes(1))
        > MAX_LOCATIONS_PER_MINUTE) {
      throw new TooManyRequestsException();
    }
    var circleEvents =
        dto.circleEvents() == null ? List.<EncryptedDataDto>of() : dto.circleEvents();
    if (circleEvents.size() > MAX_CIRCLE_EVENTS) {
      throw new InvalidEncryptedDataException();
    }
    for (var circleEvent : circleEvents) {
      if (circleEvent == null) {
        throw new InvalidEncryptedDataException();
      }
      circleEvent.toEncryptedStringOfLength(AnonymousGroupService.CIRCLE_EVENT_CIPHER_BYTES);
    }
    var saved =
        anonymousGroupService.relayMemberMessage(
            anonymousGroupId,
            authentication,
            new AGMemberMessageReqDto(dto.encryptedCoordinates(), dto.encryptedActivity()),
            lastSavedLocations.get(memberId).orElse(null));
    saved.ifPresent(timestamp -> lastSavedLocations.put(memberId, timestamp));
    presenceService.onLocation(anonymousGroupId, memberId);
    for (var circleEvent : circleEvents) {
      anonymousGroupService.relayCircleEvent(
          anonymousGroupId, authentication, new AGCircleEventReqDto(circleEvent));
    }
  }

  /** The member turned sharing off: the others learn it now rather than after the timeout. */
  public void pause(UUID anonymousGroupId, AGMemberAuthentication authentication) {
    presenceService.pause(anonymousGroupId, authentication.getId());
  }
}
