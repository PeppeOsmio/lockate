package com.peppeosmio.lockate.anonymous_group.service;

import com.peppeosmio.lockate.anonymous_group.dto.AGMemberMessageReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGPlaceEventReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGPostLocationReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGPostLocationResDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.PlacesChangedException;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * What a member sends over REST: its positions, each with the place events it caused, and the pause
 * when it stops sharing.
 */
@Service
public class AGMemberMessageService {
  static final int MAX_PLACE_EVENTS = 16;
  // the app posts every GPS fix, at most one every 2 s, 30 a minute
  static final int MAX_LOCATIONS_PER_MINUTE = 120;

  private final AnonymousGroupService anonymousGroupService;
  private final AGPresenceService presenceService;
  private final RedisService redisService;

  public AGMemberMessageService(
      AnonymousGroupService anonymousGroupService,
      AGPresenceService presenceService,
      RedisService redisService) {
    this.anonymousGroupService = anonymousGroupService;
    this.presenceService = presenceService;
    this.redisService = redisService;
  }

  /** Everything is checked before anything is relayed, so a bad request relays nothing. */
  public AGPostLocationResDto postLocation(
      UUID anonymousGroupId, AGMemberAuthentication authentication, AGPostLocationReqDto dto)
      throws TooManyRequestsException,
          InvalidEncryptedDataException,
          AGNotFoundException,
          UnauthorizedException,
          PlacesChangedException {
    var memberId = authentication.getId();
    if (redisService.increment("rate:location:" + memberId, Duration.ofMinutes(1))
        > MAX_LOCATIONS_PER_MINUTE) {
      throw new TooManyRequestsException();
    }
    var placeEvents = dto.placeEvents() == null ? List.<EncryptedDataDto>of() : dto.placeEvents();
    if (placeEvents.size() > MAX_PLACE_EVENTS) {
      throw new InvalidEncryptedDataException();
    }
    for (var placeEvent : placeEvents) {
      if (placeEvent == null) {
        throw new InvalidEncryptedDataException();
      }
      placeEvent.toEncryptedStringOfLength(AnonymousGroupService.PLACE_EVENT_CIPHER_BYTES);
    }
    // read before anything is relayed: a place change committed after it is caught by the next
    // post
    var placesVersion = anonymousGroupService.getPlacesVersion(anonymousGroupId);
    if (!placeEvents.isEmpty()
        && (dto.placesVersion() == null || dto.placesVersion() != placesVersion)) {
      throw new PlacesChangedException(placesVersion);
    }
    anonymousGroupService.relayMemberMessage(
        anonymousGroupId,
        authentication,
        new AGMemberMessageReqDto(dto.encryptedCoordinates(), dto.encryptedActivity()));
    presenceService.onLocation(anonymousGroupId, memberId);
    for (var placeEvent : placeEvents) {
      anonymousGroupService.relayPlaceEvent(
          anonymousGroupId, authentication, new AGPlaceEventReqDto(placeEvent));
    }
    return new AGPostLocationResDto(placesVersion);
  }

  /** The member turned sharing off: the others learn it now rather than after the timeout. */
  public void pause(UUID anonymousGroupId, AGMemberAuthentication authentication) {
    presenceService.pause(anonymousGroupId, authentication.getId());
  }
}
