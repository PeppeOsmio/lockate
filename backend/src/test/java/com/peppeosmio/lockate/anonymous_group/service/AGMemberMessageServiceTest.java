package com.peppeosmio.lockate.anonymous_group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.dto.AGCircleEventReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGMemberMessageReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGPostLocationReqDto;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AGMemberMessageServiceTest {

  @Mock private AnonymousGroupService anonymousGroupService;
  @Mock private AGPresenceService presenceService;
  @Mock private RedisService redisService;

  private AGMemberMessageService service;
  private final UUID groupId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final AGMemberAuthentication authentication = new AGMemberAuthentication(memberId);
  private final String rateKey = "rate:location:" + memberId;

  @BeforeEach
  void setUp() {
    service = new AGMemberMessageService(anonymousGroupService, presenceService, redisService);
  }

  private static EncryptedDataDto blobOfSize(int cipherTextBytes) {
    return new EncryptedDataDto(
        Base64.getEncoder().encodeToString(new byte[cipherTextBytes]),
        Base64.getEncoder().encodeToString(new byte[12]));
  }

  private static EncryptedDataDto circleEvent(byte marker) {
    var cipherText = new byte[AnonymousGroupService.CIRCLE_EVENT_CIPHER_BYTES];
    cipherText[0] = marker;
    return new EncryptedDataDto(
        Base64.getEncoder().encodeToString(cipherText),
        Base64.getEncoder().encodeToString(new byte[12]));
  }

  private static AGPostLocationReqDto post(List<EncryptedDataDto> circleEvents) {
    return new AGPostLocationReqDto(
        blobOfSize(AnonymousGroupService.COORDINATES_CIPHER_BYTES),
        blobOfSize(AnonymousGroupService.MEMBER_ACTIVITY_CIPHER_BYTES),
        circleEvents);
  }

  private void underTheRateLimit() {
    when(redisService.increment(rateKey, Duration.ofMinutes(1))).thenReturn(1L);
  }

  @Test
  void postLocation_relaysThePositionThenEachCircleEventInOrder() throws Exception {
    underTheRateLimit();
    when(anonymousGroupService.relayMemberMessage(eq(groupId), eq(authentication), any(), any()))
        .thenReturn(Optional.empty());
    var first = circleEvent((byte) 1);
    var second = circleEvent((byte) 2);
    var dto = post(List.of(first, second));

    service.postLocation(groupId, authentication, dto);

    var order = inOrder(anonymousGroupService, presenceService);
    order
        .verify(anonymousGroupService)
        .relayMemberMessage(
            groupId,
            authentication,
            new AGMemberMessageReqDto(dto.encryptedCoordinates(), dto.encryptedActivity()),
            null);
    order.verify(presenceService).onLocation(groupId, memberId);
    order
        .verify(anonymousGroupService)
        .relayCircleEvent(groupId, authentication, new AGCircleEventReqDto(first));
    order
        .verify(anonymousGroupService)
        .relayCircleEvent(groupId, authentication, new AGCircleEventReqDto(second));
  }

  @Test
  void postLocation_withoutCircleEvents_relaysOnlyThePosition() throws Exception {
    underTheRateLimit();
    when(anonymousGroupService.relayMemberMessage(eq(groupId), eq(authentication), any(), any()))
        .thenReturn(Optional.empty());

    service.postLocation(groupId, authentication, post(null));

    verify(presenceService).onLocation(groupId, memberId);
    verify(anonymousGroupService, never()).relayCircleEvent(any(), any(), any());
  }

  @Test
  void postLocation_passesTheTimestampItSavedLastTime() throws Exception {
    underTheRateLimit();
    var saved = LocalDateTime.now();
    when(anonymousGroupService.relayMemberMessage(eq(groupId), any(), any(), isNull()))
        .thenReturn(Optional.of(saved));
    service.postLocation(groupId, authentication, post(List.of()));
    when(anonymousGroupService.relayMemberMessage(eq(groupId), any(), any(), eq(saved)))
        .thenReturn(Optional.empty());

    service.postLocation(groupId, authentication, post(List.of()));

    var captor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(anonymousGroupService, times(2))
        .relayMemberMessage(eq(groupId), any(), any(), captor.capture());
    assertThat(captor.getAllValues()).containsExactly(null, saved);
  }

  @Test
  void postLocation_tooManyCircleEvents_throwsAndRelaysNothing() {
    underTheRateLimit();
    var events =
        new ArrayList<>(
            Collections.nCopies(
                AGMemberMessageService.MAX_CIRCLE_EVENTS + 1, circleEvent((byte) 1)));

    assertThatThrownBy(() -> service.postLocation(groupId, authentication, post(events)))
        .isInstanceOf(InvalidEncryptedDataException.class);

    verifyNoInteractions(anonymousGroupService, presenceService);
  }

  @Test
  void postLocation_aCircleEventOfTheWrongSize_throwsAndRelaysNothing() {
    underTheRateLimit();
    var events = List.of(circleEvent((byte) 1), blobOfSize(20));

    assertThatThrownBy(() -> service.postLocation(groupId, authentication, post(events)))
        .isInstanceOf(InvalidEncryptedDataException.class);

    verifyNoInteractions(anonymousGroupService, presenceService);
  }

  @Test
  void postLocation_aMissingCircleEvent_throwsAndRelaysNothing() {
    underTheRateLimit();
    var events = new ArrayList<EncryptedDataDto>();
    events.add(null);

    assertThatThrownBy(() -> service.postLocation(groupId, authentication, post(events)))
        .isInstanceOf(InvalidEncryptedDataException.class);

    verifyNoInteractions(anonymousGroupService, presenceService);
  }

  @Test
  void postLocation_invalidPosition_touchesNeitherPresenceNorCircleEvents() throws Exception {
    underTheRateLimit();
    when(anonymousGroupService.relayMemberMessage(any(), any(), any(), any()))
        .thenThrow(new InvalidEncryptedDataException());

    assertThatThrownBy(
            () ->
                service.postLocation(groupId, authentication, post(List.of(circleEvent((byte) 1)))))
        .isInstanceOf(InvalidEncryptedDataException.class);

    verifyNoInteractions(presenceService);
    verify(anonymousGroupService, never()).relayCircleEvent(any(), any(), any());
  }

  @Test
  void postLocation_overTheRateLimit_throwsAndRelaysNothing() {
    when(redisService.increment(rateKey, Duration.ofMinutes(1)))
        .thenReturn((long) AGMemberMessageService.MAX_LOCATIONS_PER_MINUTE + 1);

    assertThatThrownBy(() -> service.postLocation(groupId, authentication, post(List.of())))
        .isInstanceOf(TooManyRequestsException.class);

    verifyNoInteractions(anonymousGroupService, presenceService);
  }

  @Test
  void postLocation_atTheRateLimit_isAccepted() throws Exception {
    when(redisService.increment(rateKey, Duration.ofMinutes(1)))
        .thenReturn((long) AGMemberMessageService.MAX_LOCATIONS_PER_MINUTE);
    when(anonymousGroupService.relayMemberMessage(any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    service.postLocation(groupId, authentication, post(List.of()));

    verify(presenceService).onLocation(groupId, memberId);
  }

  @Test
  void pause_pausesThePresence() {
    service.pause(groupId, authentication);

    verify(presenceService).pause(groupId, memberId);
  }
}
