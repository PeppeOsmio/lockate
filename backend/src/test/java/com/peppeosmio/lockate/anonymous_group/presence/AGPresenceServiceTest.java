package com.peppeosmio.lockate.anonymous_group.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.classes.AGMemberRef;
import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.push.AGPushService;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AGPresenceServiceTest {

  @Mock private RedisService redisService;
  @Mock private AGPushService pushService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AGPresenceService service;
  private Consumer<String> onExpired;
  private final UUID groupId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final String presenceKey = "ag-presence:" + groupId + ":" + memberId;
  private final String onlineKey = "ag-online:" + groupId + ":" + memberId;
  private final String events = "ag-" + groupId + "-events";

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    service = new AGPresenceService(redisService, objectMapper, pushService);
    var captor = ArgumentCaptor.forClass(Consumer.class);
    verify(redisService).enableExpiredEvents();
    verify(redisService).subscribePattern(eq("__keyevent@*__:expired"), captor.capture());
    onExpired = captor.getValue();
  }

  @Test
  void timeouts_matchTheGreyDotAndOutliveTwoLostStillFixes() {
    assertThat(AGPresenceService.TIMEOUT).isEqualTo(Duration.ofMinutes(30));
    assertThat(AGPresenceService.ONLINE_TTL).isEqualTo(Duration.ofMinutes(31));
  }

  private AGFrameDto published() {
    var captor = ArgumentCaptor.forClass(String.class);
    verify(redisService).publish(eq(events), captor.capture());
    return objectMapper.readValue(captor.getValue(), AGFrameDto.class);
  }

  @Test
  void onLocation_memberWasOffline_announcesOnline() {
    when(redisService.setAndGet(eq(onlineKey), anyString(), eq(AGPresenceService.ONLINE_TTL)))
        .thenReturn(Optional.empty());

    service.onLocation(groupId, memberId);

    var frame = published();
    assertThat(frame.type()).isEqualTo(AGFrameDto.MEMBER_PRESENCE);
    assertThat(frame.memberId()).isEqualTo(memberId);
    assertThat(frame.online()).isTrue();
    verify(pushService).notifyGroup(groupId, memberId, frame);
  }

  @Test
  void onLocation_memberAlreadyOnline_announcesNothing() {
    when(redisService.setAndGet(eq(onlineKey), anyString(), any()))
        .thenReturn(Optional.of(LocalDateTime.now().toString()));

    service.onLocation(groupId, memberId);

    verify(redisService, never()).publish(anyString(), anyString());
    verifyNoInteractions(pushService);
  }

  @Test
  void onLocation_refreshesPresenceBeforeOnline() {
    when(redisService.setAndGet(anyString(), anyString(), any())).thenReturn(Optional.of("x"));

    service.onLocation(groupId, memberId);

    var order = inOrder(redisService);
    order.verify(redisService).saveValue(presenceKey, "1", AGPresenceService.TIMEOUT);
    order.verify(redisService).setAndGet(eq(onlineKey), anyString(), any());
  }

  @Test
  void presenceExpired_claimed_announcesOfflineWithTheLastSeen() {
    var lastSeen = LocalDateTime.of(2026, 11, 8, 12, 3);
    when(redisService.runScript(
            AGPresenceService.CLAIM_OFFLINE_SCRIPT, List.of(presenceKey, onlineKey)))
        .thenReturn(Optional.of(lastSeen.toString()));

    onExpired.accept(presenceKey);

    var frame = published();
    assertThat(frame.online()).isFalse();
    assertThat(frame.lastSeen()).isEqualTo(lastSeen);
    verify(pushService).notifyGroup(groupId, memberId, frame);
  }

  @Test
  void presenceExpired_notClaimed_announcesNothing() {
    when(redisService.runScript(anyString(), any())).thenReturn(Optional.empty());

    onExpired.accept(presenceKey);

    verify(redisService, never()).publish(anyString(), anyString());
    verifyNoInteractions(pushService);
  }

  @Test
  void otherKeyExpired_isIgnored() {
    onExpired.accept("invite:" + UUID.randomUUID());

    verify(redisService, never()).runScript(anyString(), any());
  }

  @Test
  void pause_memberOnline_announcesOfflineAtOnce() {
    var lastSeen = LocalDateTime.of(2026, 11, 8, 12, 3);
    when(redisService.getAndDelete(onlineKey)).thenReturn(Optional.of(lastSeen.toString()));

    service.pause(groupId, memberId);

    verify(redisService).deleteValue(presenceKey);
    var frame = published();
    assertThat(frame.online()).isFalse();
    assertThat(frame.lastSeen()).isEqualTo(lastSeen);
    verify(pushService).notifyGroup(groupId, memberId, frame);
  }

  @Test
  void pause_memberNotOnline_announcesNothing() {
    when(redisService.getAndDelete(onlineKey)).thenReturn(Optional.empty());

    service.pause(groupId, memberId);

    verify(redisService, never()).publish(anyString(), anyString());
  }

  @Test
  void forget_deletesBothKeysSilently() {
    service.forget(groupId, memberId);

    verify(redisService).deleteValue(presenceKey);
    verify(redisService).deleteValue(onlineKey);
    verify(redisService, never()).publish(anyString(), anyString());
  }

  @Test
  void online_returnsTheMembersWithAnOnlineKey() {
    var groupId = UUID.randomUUID();
    var online = new AGMemberRef(groupId, UUID.randomUUID());
    var offline = new AGMemberRef(groupId, UUID.randomUUID());
    when(redisService.getValues(
            List.of(
                "ag-online:" + groupId + ":" + online.memberId(),
                "ag-online:" + groupId + ":" + offline.memberId())))
        .thenReturn(java.util.Arrays.asList("2026-11-20T22:00", null));

    assertThat(service.online(List.of(online, offline))).containsExactly(online);
  }

  @Test
  void online_noMembers_asksRedisNothing() {
    assertThat(service.online(List.of())).isEmpty();
    verify(redisService, never()).getValues(any());
  }

  @Test
  void onlineCount_countsTheOnlineKeys() {
    when(redisService.countKeys("ag-online:*")).thenReturn(4L);

    assertThat(service.onlineCount()).isEqualTo(4);
  }
}
