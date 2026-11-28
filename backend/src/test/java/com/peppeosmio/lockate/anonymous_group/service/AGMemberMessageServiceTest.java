package com.peppeosmio.lockate.anonymous_group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.dto.AGMemberMessageReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGPlaceEventReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGPostLocationReqDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.PlacesChangedException;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  void setUp() throws Exception {
    service = new AGMemberMessageService(anonymousGroupService, presenceService, redisService);
    // the version the posts below were computed on, unless a test says otherwise
    lenient().when(anonymousGroupService.getPlacesVersion(groupId)).thenReturn(PLACES_VERSION);
  }

  private static final long PLACES_VERSION = 4;

  private static EncryptedDataDto blobOfSize(int cipherTextBytes) {
    return new EncryptedDataDto(
        Base64.getEncoder().encodeToString(new byte[cipherTextBytes]),
        Base64.getEncoder().encodeToString(new byte[12]));
  }

  private static EncryptedDataDto placeEvent(byte marker) {
    var cipherText = new byte[AnonymousGroupService.PLACE_EVENT_CIPHER_BYTES];
    cipherText[0] = marker;
    return new EncryptedDataDto(
        Base64.getEncoder().encodeToString(cipherText),
        Base64.getEncoder().encodeToString(new byte[12]));
  }

  private static AGPostLocationReqDto post(List<EncryptedDataDto> placeEvents) {
    return post(placeEvents, PLACES_VERSION);
  }

  private static AGPostLocationReqDto post(List<EncryptedDataDto> placeEvents, Long placesVersion) {
    return new AGPostLocationReqDto(
        blobOfSize(AnonymousGroupService.COORDINATES_CIPHER_BYTES),
        blobOfSize(AnonymousGroupService.MEMBER_ACTIVITY_CIPHER_BYTES),
        placeEvents,
        placesVersion);
  }

  private void underTheRateLimit() {
    when(redisService.increment(rateKey, Duration.ofMinutes(1))).thenReturn(1L);
  }

  @Test
  void postLocation_relaysThePositionThenEachPlaceEventInOrder() throws Exception {
    underTheRateLimit();
    var first = placeEvent((byte) 1);
    var second = placeEvent((byte) 2);
    var dto = post(List.of(first, second));

    service.postLocation(groupId, authentication, dto);

    var order = inOrder(anonymousGroupService, presenceService);
    order
        .verify(anonymousGroupService)
        .relayMemberMessage(
            groupId,
            authentication,
            new AGMemberMessageReqDto(dto.encryptedCoordinates(), dto.encryptedActivity()));
    order.verify(presenceService).onLocation(groupId, memberId);
    order
        .verify(anonymousGroupService)
        .relayPlaceEvent(groupId, authentication, new AGPlaceEventReqDto(first));
    order
        .verify(anonymousGroupService)
        .relayPlaceEvent(groupId, authentication, new AGPlaceEventReqDto(second));
  }

  @Test
  void postLocation_answersTheGroupsPlacesVersion() throws Exception {
    underTheRateLimit();

    var res = service.postLocation(groupId, authentication, post(null));

    assertThat(res.placesVersion()).isEqualTo(PLACES_VERSION);
  }

  @Test
  void postLocation_eventsOnOtherPlaces_throwsPlacesChangedAndRelaysNothing() throws Exception {
    underTheRateLimit();

    assertThatThrownBy(
            () ->
                service.postLocation(
                    groupId,
                    authentication,
                    post(List.of(placeEvent((byte) 1)), PLACES_VERSION - 1)))
        .isInstanceOfSatisfying(
            PlacesChangedException.class,
            e -> assertThat(e.getPlacesVersion()).isEqualTo(PLACES_VERSION));
    verifyNoInteractions(presenceService);
    verify(anonymousGroupService, never()).relayMemberMessage(any(), any(), any());
    verify(anonymousGroupService, never()).relayPlaceEvent(any(), any(), any());
  }

  @Test
  void postLocation_eventsWithoutAVersion_throwsPlacesChanged() {
    underTheRateLimit();

    assertThatThrownBy(
            () ->
                service.postLocation(
                    groupId, authentication, post(List.of(placeEvent((byte) 1)), null)))
        .isInstanceOf(PlacesChangedException.class);
  }

  @Test
  void postLocation_noEventsOnOtherPlaces_relaysThePositionAndAnswersTheNewVersion()
      throws Exception {
    underTheRateLimit();

    var res = service.postLocation(groupId, authentication, post(List.of(), null));

    verify(presenceService).onLocation(groupId, memberId);
    assertThat(res.placesVersion()).isEqualTo(PLACES_VERSION);
  }

  @Test
  void postLocation_withoutPlaceEvents_relaysOnlyThePosition() throws Exception {
    underTheRateLimit();

    service.postLocation(groupId, authentication, post(null));

    verify(presenceService).onLocation(groupId, memberId);
    verify(anonymousGroupService, never()).relayPlaceEvent(any(), any(), any());
  }

  @Test
  void postLocation_tooManyPlaceEvents_throwsAndRelaysNothing() {
    underTheRateLimit();
    var events =
        new ArrayList<>(
            Collections.nCopies(AGMemberMessageService.MAX_PLACE_EVENTS + 1, placeEvent((byte) 1)));

    assertThatThrownBy(() -> service.postLocation(groupId, authentication, post(events)))
        .isInstanceOf(InvalidEncryptedDataException.class);

    verifyNoInteractions(anonymousGroupService, presenceService);
  }

  @Test
  void postLocation_aPlaceEventOfTheWrongSize_throwsAndRelaysNothing() {
    underTheRateLimit();
    var events = List.of(placeEvent((byte) 1), blobOfSize(20));

    assertThatThrownBy(() -> service.postLocation(groupId, authentication, post(events)))
        .isInstanceOf(InvalidEncryptedDataException.class);

    verifyNoInteractions(anonymousGroupService, presenceService);
  }

  @Test
  void postLocation_aMissingPlaceEvent_throwsAndRelaysNothing() {
    underTheRateLimit();
    var events = new ArrayList<EncryptedDataDto>();
    events.add(null);

    assertThatThrownBy(() -> service.postLocation(groupId, authentication, post(events)))
        .isInstanceOf(InvalidEncryptedDataException.class);

    verifyNoInteractions(anonymousGroupService, presenceService);
  }

  @Test
  void postLocation_invalidPosition_touchesNeitherPresenceNorPlaceEvents() throws Exception {
    underTheRateLimit();
    doThrow(new InvalidEncryptedDataException())
        .when(anonymousGroupService)
        .relayMemberMessage(any(), any(), any());

    assertThatThrownBy(
            () ->
                service.postLocation(groupId, authentication, post(List.of(placeEvent((byte) 1)))))
        .isInstanceOf(InvalidEncryptedDataException.class);

    verifyNoInteractions(presenceService);
    verify(anonymousGroupService, never()).relayPlaceEvent(any(), any(), any());
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

    service.postLocation(groupId, authentication, post(List.of()));

    verify(presenceService).onLocation(groupId, memberId);
  }

  @Test
  void pause_pausesThePresence() {
    service.pause(groupId, authentication);

    verify(presenceService).pause(groupId, memberId);
  }
}
