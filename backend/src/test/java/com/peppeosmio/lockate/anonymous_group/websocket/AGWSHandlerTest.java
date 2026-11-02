package com.peppeosmio.lockate.anonymous_group.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGMemberMessageReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.LocationRecordDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
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
  @Mock private WebSocketSession session;
  @Mock private MessageListener redisListener;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AGWSHandler handler;
  private Map<String, Object> sessionAttributes;
  private final UUID groupId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    handler = new AGWSHandler(objectMapper, anonymousGroupService, redisService);
    sessionAttributes = new HashMap<>();
    sessionAttributes.put("anonymousGroupId", groupId);
    sessionAttributes.put("authentication", new AGMemberAuthentication(memberId));
    when(session.getAttributes()).thenReturn(sessionAttributes);
    lenient().when(session.getId()).thenReturn("session-id");
  }

  private TextMessage locationMessage() throws Exception {
    var dto =
        new AGMemberMessageReqDto(
            new EncryptedDataDto(
                java.util.Base64.getEncoder().encodeToString("coord".getBytes()),
                java.util.Base64.getEncoder().encodeToString("iv".getBytes())));
    return new TextMessage(objectMapper.writeValueAsString(dto));
  }

  @SuppressWarnings("unchecked")
  private Consumer<String> connect() throws Exception {
    var consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    when(redisService.subscribe(eq("ag-" + groupId), consumerCaptor.capture()))
        .thenReturn(redisListener);
    when(anonymousGroupService.getSnapshot(groupId))
        .thenReturn(AGFrameDto.snapshot(java.util.List.of(), java.util.List.of()));
    handler.afterConnectionEstablished(session);
    return consumerCaptor.getValue();
  }

  private String json(AGFrameDto frame) {
    return objectMapper.writeValueAsString(frame);
  }

  private AGFrameDto messageFrom(UUID author) {
    return AGFrameDto.memberMessage(
        author,
        new LocationRecordDto(
            new EncryptedDataDto(
                java.util.Base64.getEncoder().encodeToString("p".getBytes()),
                java.util.Base64.getEncoder().encodeToString("iv".getBytes())),
            LocalDateTime.now()));
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
    when(redisService.subscribe(eq("ag-" + groupId), consumerCaptor.capture()))
        .thenReturn(redisListener);
    var other = UUID.randomUUID();
    when(anonymousGroupService.getSnapshot(groupId))
        .thenAnswer(
            i -> {
              @SuppressWarnings("unchecked")
              Consumer<String> consumer = consumerCaptor.getValue();
              consumer.accept(json(messageFrom(other)));
              return AGFrameDto.snapshot(java.util.List.of(), java.util.List.of());
            });

    handler.afterConnectionEstablished(session);

    var sent = sentPayloads();
    assertThat(sent).hasSize(2);
    assertThat(objectMapper.readValue(sent.get(0), AGFrameDto.class).type())
        .isEqualTo(AGFrameDto.SNAPSHOT);
    assertThat(objectMapper.readValue(sent.get(1), AGFrameDto.class).type())
        .isEqualTo(AGFrameDto.MEMBER_MESSAGE);
  }

  @Test
  void afterConnectionEstablished_missingGroup_closesAsGroupDeleted() throws Exception {
    when(redisService.subscribe(any(), any())).thenReturn(redisListener);
    when(anonymousGroupService.getSnapshot(groupId)).thenThrow(new AGNotFoundException(groupId));

    handler.afterConnectionEstablished(session);

    verify(session).close(AGWSHandler.GROUP_DELETED);
  }

  @Test
  void redisFrame_ownMemberMessage_isNotSentBack() throws Exception {
    var consumer = connect();

    consumer.accept(json(messageFrom(memberId)));

    assertThat(sentPayloads()).hasSize(1);
  }

  @Test
  void redisFrame_otherMemberMessage_isRelayedUnchanged() throws Exception {
    var consumer = connect();
    var frame = json(messageFrom(UUID.randomUUID()));

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

    verify(redisService).unsubscribe("ag-" + groupId, redisListener);
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
}
