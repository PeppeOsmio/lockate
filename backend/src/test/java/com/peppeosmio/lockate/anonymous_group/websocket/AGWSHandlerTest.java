package com.peppeosmio.lockate.anonymous_group.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.dto.AGCircleEventReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGMemberMessageReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.CircleEventDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AGWSHandlerTest {

  @Mock private AnonymousGroupService anonymousGroupService;
  @Mock private RedisService redisService;
  @Mock private AGPresenceService presenceService;
  @Mock private WebSocketSession session;
  @Mock private MessageListener redisListener;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AGWSHandler handler;
  private Map<String, Object> sessionAttributes;
  private final UUID groupId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    handler = new AGWSHandler(objectMapper, anonymousGroupService, redisService, presenceService);
    sessionAttributes = new HashMap<>();
    sessionAttributes.put("anonymousGroupId", groupId);
    sessionAttributes.put("authentication", new AGMemberAuthentication(memberId));
    when(session.getAttributes()).thenReturn(sessionAttributes);
    lenient().when(session.getId()).thenReturn("session-id");
  }

  private static EncryptedDataDto blob(String text) {
    return new EncryptedDataDto(
        java.util.Base64.getEncoder().encodeToString(text.getBytes()),
        java.util.Base64.getEncoder().encodeToString("iv".getBytes()));
  }

  private TextMessage locationMessage() throws Exception {
    return new TextMessage(
        objectMapper.writeValueAsString(
            Map.of(
                "type", "member.location",
                "encryptedCoordinates", blob("coord"),
                "encryptedActivity", blob("activity"))));
  }

  private TextMessage circleEventMessage() throws Exception {
    return new TextMessage(
        objectMapper.writeValueAsString(
            Map.of("type", "circle.event", "encryptedEvent", blob("event"))));
  }

  @SuppressWarnings("unchecked")
  private Consumer<String> connect() throws Exception {
    var consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    when(redisService.subscribe(eq("ag-" + groupId + "-events"), consumerCaptor.capture()))
        .thenReturn(redisListener);
    when(anonymousGroupService.getSnapshot(groupId))
        .thenReturn(AGFrameDto.snapshot(java.util.List.of(), java.util.List.of()));
    handler.afterConnectionEstablished(session);
    return consumerCaptor.getValue();
  }

  private String json(AGFrameDto frame) {
    return objectMapper.writeValueAsString(frame);
  }

  private AGFrameDto circleEventFrom(UUID author) {
    return AGFrameDto.circleEventOf(author, new CircleEventDto(blob("e"), LocalDateTime.now()));
  }

  private java.util.List<String> sentPayloads() throws Exception {
    var captor = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.atLeast(0)).sendMessage(captor.capture());
    return captor.getAllValues().stream().map(TextMessage::getPayload).toList();
  }

  @Test
  void afterConnectionEstablished_sendsTheSnapshotFirst() throws Exception {
    connect();

    var sent = sentPayloads();
    assertThat(sent).hasSize(1);
    assertThat(objectMapper.readValue(sent.get(0), AGFrameDto.class).type())
        .isEqualTo(AGFrameDto.SNAPSHOT);
  }

  @Test
  void afterConnectionEstablished_framesArrivingDuringTheSnapshotAreSentAfterIt() throws Exception {
    var consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    when(redisService.subscribe(eq("ag-" + groupId + "-events"), consumerCaptor.capture()))
        .thenReturn(redisListener);
    var other = UUID.randomUUID();
    when(anonymousGroupService.getSnapshot(groupId))
        .thenAnswer(
            i -> {
              @SuppressWarnings("unchecked")
              Consumer<String> consumer = consumerCaptor.getValue();
              consumer.accept(json(circleEventFrom(other)));
              return AGFrameDto.snapshot(java.util.List.of(), java.util.List.of());
            });

    handler.afterConnectionEstablished(session);

    var sent = sentPayloads();
    assertThat(sent).hasSize(2);
    assertThat(objectMapper.readValue(sent.get(0), AGFrameDto.class).type())
        .isEqualTo(AGFrameDto.SNAPSHOT);
    assertThat(objectMapper.readValue(sent.get(1), AGFrameDto.class).type())
        .isEqualTo(AGFrameDto.CIRCLE_EVENT);
  }

  @Test
  void afterConnectionEstablished_frameArrivingWhileSubscribing_isSentAfterTheSnapshot()
      throws Exception {
    var other = UUID.randomUUID();
    when(redisService.subscribe(eq("ag-" + groupId + "-events"), any()))
        .thenAnswer(
            i -> {
              i.<Consumer<String>>getArgument(1).accept(json(circleEventFrom(other)));
              return redisListener;
            });
    when(anonymousGroupService.getSnapshot(groupId))
        .thenReturn(AGFrameDto.snapshot(java.util.List.of(), java.util.List.of()));

    handler.afterConnectionEstablished(session);

    var sent = sentPayloads();
    assertThat(sent).hasSize(2);
    assertThat(objectMapper.readValue(sent.get(0), AGFrameDto.class).type())
        .isEqualTo(AGFrameDto.SNAPSHOT);
    assertThat(objectMapper.readValue(sent.get(1), AGFrameDto.class).type())
        .isEqualTo(AGFrameDto.CIRCLE_EVENT);
  }

  @Test
  void afterConnectionEstablished_frameArrivingWhileHeldOnesAreSent_isSentAfterThem()
      throws Exception {
    var consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    when(redisService.subscribe(eq("ag-" + groupId + "-events"), consumerCaptor.capture()))
        .thenReturn(redisListener);
    var first = json(circleEventFrom(UUID.randomUUID()));
    var second = json(circleEventFrom(UUID.randomUUID()));
    var third = json(circleEventFrom(UUID.randomUUID()));
    when(anonymousGroupService.getSnapshot(groupId))
        .thenAnswer(
            i -> {
              @SuppressWarnings("unchecked")
              Consumer<String> consumer = consumerCaptor.getValue();
              consumer.accept(first);
              consumer.accept(second);
              return AGFrameDto.snapshot(java.util.List.of(), java.util.List.of());
            });
    var late = new Thread[1];
    doAnswer(
            i -> {
              if (i.<TextMessage>getArgument(0).getPayload().equals(first)) {
                @SuppressWarnings("unchecked")
                Consumer<String> consumer = consumerCaptor.getValue();
                late[0] = new Thread(() -> consumer.accept(third));
                late[0].start();
                var deadline = System.nanoTime() + 2_000_000_000L;
                while (late[0].getState() != Thread.State.BLOCKED
                    && late[0].getState() != Thread.State.TERMINATED
                    && System.nanoTime() < deadline) {
                  Thread.sleep(5);
                }
              }
              return null;
            })
        .when(session)
        .sendMessage(any(TextMessage.class));

    handler.afterConnectionEstablished(session);
    late[0].join(2000);

    assertThat(sentPayloads().subList(1, 4)).containsExactly(first, second, third);
  }

  @Test
  void afterConnectionEstablished_missingGroup_closesAsGroupDeleted() throws Exception {
    when(redisService.subscribe(any(), any())).thenReturn(redisListener);
    when(anonymousGroupService.getSnapshot(groupId)).thenThrow(new AGNotFoundException(groupId));

    handler.afterConnectionEstablished(session);

    verify(session).close(AGWSHandler.GROUP_DELETED);
  }

  @Test
  void redisFrame_ownCircleEvent_isNotSentBack() throws Exception {
    var consumer = connect();

    consumer.accept(json(circleEventFrom(memberId)));

    assertThat(sentPayloads()).hasSize(1);
  }

  @Test
  void redisFrame_ownPresence_isNotSentBack() throws Exception {
    var consumer = connect();

    consumer.accept(json(AGFrameDto.memberPresence(memberId, true, null)));

    assertThat(sentPayloads()).hasSize(1);
  }

  @Test
  void redisFrame_otherPresence_isRelayedUnchanged() throws Exception {
    var consumer = connect();
    var frame = json(AGFrameDto.memberPresence(UUID.randomUUID(), false, LocalDateTime.now()));

    consumer.accept(frame);

    assertThat(sentPayloads()).containsExactly(sentPayloads().get(0), frame);
  }

  @Test
  void redisFrame_otherCircleEvent_isRelayedUnchanged() throws Exception {
    var consumer = connect();
    var frame = json(circleEventFrom(UUID.randomUUID()));

    consumer.accept(frame);

    assertThat(sentPayloads()).containsExactly(sentPayloads().get(0), frame);
  }

  @Test
  void redisFrame_circleAndMemberFrames_areRelayed() throws Exception {
    var consumer = connect();

    consumer.accept(json(AGFrameDto.circleDeleted(UUID.randomUUID())));
    consumer.accept(json(AGFrameDto.memberLeft(UUID.randomUUID())));

    assertThat(sentPayloads()).hasSize(3);
  }

  @Test
  void redisFrame_ownMemberLeft_closesAsRemoved() throws Exception {
    var consumer = connect();

    consumer.accept(json(AGFrameDto.memberLeft(memberId)));

    verify(session).close(AGWSHandler.MEMBER_REMOVED);
    assertThat(sentPayloads()).hasSize(1);
  }

  @Test
  void redisFrame_groupDeleted_closesAndIsNotSent() throws Exception {
    var consumer = connect();

    consumer.accept(json(AGFrameDto.groupDeleted()));

    verify(session).close(AGWSHandler.GROUP_DELETED);
    assertThat(sentPayloads()).hasSize(1);
  }

  @Test
  void afterConnectionClosed_unsubscribesFromRedis() throws Exception {
    connect();

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(redisService).unsubscribe("ag-" + groupId + "-events", redisListener);
  }

  @Test
  void afterConnectionEstablished_missingAuthentication_throwsUnauthorized() {
    sessionAttributes.remove("authentication");

    assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void handleTextMessage_firstMessage_passesNullLastSavedTimestamp() throws Exception {
    when(anonymousGroupService.relayMemberMessage(eq(groupId), any(), any(), any()))
        .thenReturn(Optional.of(LocalDateTime.now()));

    handler.handleTextMessage(session, locationMessage());

    verify(anonymousGroupService).relayMemberMessage(eq(groupId), any(), any(), isNull());
  }

  @Test
  void handleTextMessage_rapidSecondMessage_passesPreviouslySavedTimestampFromCache()
      throws Exception {
    var firstTimestamp = LocalDateTime.now();
    when(anonymousGroupService.relayMemberMessage(eq(groupId), any(), any(), isNull()))
        .thenReturn(Optional.of(firstTimestamp));
    handler.handleTextMessage(session, locationMessage());

    when(anonymousGroupService.relayMemberMessage(eq(groupId), any(), any(), eq(firstTimestamp)))
        .thenReturn(Optional.empty());
    handler.handleTextMessage(session, locationMessage());

    var timestampCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(anonymousGroupService, times(2))
        .relayMemberMessage(eq(groupId), any(), any(), timestampCaptor.capture());
    assertThat(timestampCaptor.getAllValues()).containsExactly(null, firstTimestamp);
  }

  @Test
  void handleTextMessage_agNotFoundException_closesWithBadData() throws Exception {
    when(anonymousGroupService.relayMemberMessage(any(), any(), any(), any()))
        .thenThrow(new AGNotFoundException(groupId));

    handler.handleTextMessage(session, locationMessage());

    verify(session).close(CloseStatus.BAD_DATA);
  }

  @Test
  void handleTextMessage_unauthorizedException_closesWithBadData() throws Exception {
    when(anonymousGroupService.relayMemberMessage(any(), any(), any(), any()))
        .thenThrow(new UnauthorizedException());

    handler.handleTextMessage(session, locationMessage());

    verify(session).close(CloseStatus.BAD_DATA);
  }

  @Test
  void handleTextMessage_invalidEncryptedData_closesWithBadData() throws Exception {
    when(anonymousGroupService.relayMemberMessage(any(), any(), any(), any()))
        .thenThrow(new InvalidEncryptedDataException());

    handler.handleTextMessage(session, locationMessage());

    verify(session).close(CloseStatus.BAD_DATA);
  }

  @Test
  void handleTextMessage_missingAuthentication_closesWithBadData() throws Exception {
    sessionAttributes.remove("authentication");

    handler.handleTextMessage(session, locationMessage());

    verify(session).close(CloseStatus.BAD_DATA);
  }

  @Test
  void handleTextMessage_unexpectedException_closesWithServerError() throws Exception {
    when(anonymousGroupService.relayMemberMessage(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("boom"));

    handler.handleTextMessage(session, locationMessage());

    verify(session).close(CloseStatus.SERVER_ERROR);
  }

  @Test
  void handleTextMessage_memberLocation_relaysTheEncryptedFields() throws Exception {
    when(anonymousGroupService.relayMemberMessage(eq(groupId), any(), any(), any()))
        .thenReturn(Optional.empty());

    handler.handleTextMessage(session, locationMessage());

    var captor = ArgumentCaptor.forClass(AGMemberMessageReqDto.class);
    verify(anonymousGroupService).relayMemberMessage(eq(groupId), any(), captor.capture(), any());
    assertThat(captor.getValue())
        .isEqualTo(new AGMemberMessageReqDto(blob("coord"), blob("activity")));
    verify(presenceService).onLocation(groupId, memberId);
  }

  @Test
  void handleTextMessage_memberPaused_pausesThePresence() throws Exception {
    handler.handleTextMessage(
        session, new TextMessage(objectMapper.writeValueAsString(Map.of("type", "member.paused"))));

    verify(presenceService).pause(groupId, memberId);
    verify(session, never()).close(any());
  }

  @Test
  void handleTextMessage_invalidLocation_doesntTouchThePresence() throws Exception {
    when(anonymousGroupService.relayMemberMessage(eq(groupId), any(), any(), any()))
        .thenThrow(new InvalidEncryptedDataException());

    handler.handleTextMessage(session, locationMessage());

    verifyNoInteractions(presenceService);
  }

  @Test
  void handleTextMessage_circleEvent_relaysIt() throws Exception {
    handler.handleTextMessage(session, circleEventMessage());

    var authentication = ArgumentCaptor.forClass(AGMemberAuthentication.class);
    verify(anonymousGroupService)
        .relayCircleEvent(
            eq(groupId), authentication.capture(), eq(new AGCircleEventReqDto(blob("event"))));
    assertThat(authentication.getValue().getId()).isEqualTo(memberId);
    verify(session, never()).close(any());
  }

  @Test
  void handleTextMessage_invalidCircleEvent_closesWithBadData() throws Exception {
    doThrow(new InvalidEncryptedDataException())
        .when(anonymousGroupService)
        .relayCircleEvent(any(), any(), any());

    handler.handleTextMessage(session, circleEventMessage());

    verify(session).close(CloseStatus.BAD_DATA);
  }

  @Test
  void handleTextMessage_unknownType_closesWithBadData() throws Exception {
    handler.handleTextMessage(
        session, new TextMessage(objectMapper.writeValueAsString(Map.of("type", "nope"))));

    verify(session).close(CloseStatus.BAD_DATA);
    verifyNoInteractions(anonymousGroupService);
  }

  private TextMessage pingMessage() throws Exception {
    return new TextMessage(objectMapper.writeValueAsString(Map.of("type", "ping")));
  }

  @Test
  void handleTextMessage_ping_answersPongAndLeavesThePresenceAlone() throws Exception {
    connect();

    handler.handleTextMessage(session, pingMessage());

    var sent = sentPayloads();
    assertThat(sent).hasSize(2);
    assertThat(objectMapper.readValue(sent.get(1), AGFrameDto.class).type())
        .isEqualTo(AGFrameDto.PONG);
    verifyNoInteractions(presenceService);
    verify(session, never()).close(any());
  }

  @Test
  void closeIdleConnections_silentPastTheTimeout_closesAndUnsubscribes() throws Exception {
    handler.now = () -> 0L;
    connect();

    handler.now = () -> AGWSHandler.IDLE_TIMEOUT_MILLIS + 1;
    handler.closeIdleConnections();

    verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
    verify(redisService).unsubscribe("ag-" + groupId + "-events", redisListener);
  }

  @Test
  void closeIdleConnections_withinTheTimeout_keepsTheConnection() throws Exception {
    handler.now = () -> 0L;
    connect();

    handler.now = () -> AGWSHandler.IDLE_TIMEOUT_MILLIS;
    handler.closeIdleConnections();

    verify(session, never()).close(any());
    verify(redisService, never()).unsubscribe(any(), any());
  }

  @Test
  void closeIdleConnections_anyFrameRefreshesTheDeadline() throws Exception {
    handler.now = () -> 0L;
    connect();
    handler.now = () -> 100_000L;
    handler.handleTextMessage(
        session, new TextMessage(objectMapper.writeValueAsString(Map.of("type", "member.paused"))));

    handler.now = () -> AGWSHandler.IDLE_TIMEOUT_MILLIS + 1;
    handler.closeIdleConnections();

    verify(session, never()).close(any());
  }

  @Test
  void closeIdleConnections_closeFails_stillUnsubscribes() throws Exception {
    handler.now = () -> 0L;
    connect();
    doThrow(new java.io.IOException("dead")).when(session).close(any());

    handler.now = () -> AGWSHandler.IDLE_TIMEOUT_MILLIS + 1;
    handler.closeIdleConnections();
    handler.closeIdleConnections();

    verify(redisService, times(1)).unsubscribe("ag-" + groupId + "-events", redisListener);
  }

  @Test
  void handleTextMessage_missingType_closesWithBadData() throws Exception {
    handler.handleTextMessage(
        session,
        new TextMessage(objectMapper.writeValueAsString(Map.of("encryptedEvent", blob("event")))));

    verify(session).close(CloseStatus.BAD_DATA);
    verifyNoInteractions(anonymousGroupService);
  }
}
