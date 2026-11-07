package com.peppeosmio.lockate.anonymous_group.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.dto.LocationRecordDto;
import com.peppeosmio.lockate.anonymous_group.dto.MemberLocationDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.redis.RedisService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AGLocationStreamsTest {

  @Mock private AnonymousGroupService anonymousGroupService;
  @Mock private RedisService redisService;
  @Mock private MessageListener locationsListener;
  @Mock private MessageListener eventsListener;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AGLocationStreams streams;
  private final UUID groupId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final ArgumentCaptor<Consumer<String>> locations = consumerCaptor();
  private final ArgumentCaptor<Consumer<String>> events = consumerCaptor();

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Consumer<String>> consumerCaptor() {
    return ArgumentCaptor.forClass(Consumer.class);
  }

  /** Records what would be written to the response, and whether the stream was ended. */
  private static class RecordingEmitter extends SseEmitter {
    final List<String> sent = new ArrayList<>();
    boolean completed = false;
    boolean failing = false;

    @Override
    public synchronized void send(SseEventBuilder builder) throws IOException {
      if (failing) {
        throw new IOException("gone");
      }
      sent.add(
          builder.build().stream()
              .map(part -> part.getData().toString())
              .collect(Collectors.joining()));
    }

    @Override
    public synchronized void complete() {
      completed = true;
    }
  }

  @BeforeEach
  void setUp() {
    streams = new AGLocationStreams(objectMapper, anonymousGroupService, redisService);
  }

  @AfterEach
  void tearDown() {
    streams.shutdown();
  }

  private static EncryptedDataDto blob(String text) {
    return new EncryptedDataDto(
        java.util.Base64.getEncoder().encodeToString(text.getBytes()),
        java.util.Base64.getEncoder().encodeToString("iv".getBytes()));
  }

  private MemberLocationDto location(UUID member) {
    return new MemberLocationDto(
        member, new LocationRecordDto(blob("c"), blob("a"), LocalDateTime.now()));
  }

  private String json(Object value) {
    return objectMapper.writeValueAsString(value);
  }

  private RecordingEmitter open(List<MemberLocationDto> snapshot) throws Exception {
    when(redisService.subscribe(eq("ag-" + groupId + "-locations"), locations.capture()))
        .thenReturn(locationsListener);
    when(redisService.subscribe(eq("ag-" + groupId + "-events"), events.capture()))
        .thenReturn(eventsListener);
    when(anonymousGroupService.getLocationSnapshot(groupId)).thenReturn(snapshot);
    var emitter = new RecordingEmitter();
    streams.open(groupId, memberId, emitter);
    return emitter;
  }

  @Test
  void open_sendsTheSnapshotFirst() throws Exception {
    var snapshot = List.of(location(UUID.randomUUID()));

    var emitter = open(snapshot);

    assertThat(emitter.sent).hasSize(1);
    assertThat(emitter.sent.get(0)).contains("event:snapshot").contains(json(snapshot));
  }

  @Test
  void open_locationsArrivingDuringTheSnapshotAreSentAfterIt() throws Exception {
    var early = json(location(UUID.randomUUID()));
    when(redisService.subscribe(eq("ag-" + groupId + "-locations"), locations.capture()))
        .thenReturn(locationsListener);
    when(redisService.subscribe(eq("ag-" + groupId + "-events"), events.capture()))
        .thenReturn(eventsListener);
    when(anonymousGroupService.getLocationSnapshot(groupId))
        .thenAnswer(
            i -> {
              locations.getValue().accept(early);
              return List.of();
            });
    var emitter = new RecordingEmitter();

    streams.open(groupId, memberId, emitter);

    assertThat(emitter.sent).hasSize(2);
    assertThat(emitter.sent.get(0)).contains("event:snapshot");
    assertThat(emitter.sent.get(1)).contains("event:location").contains(early);
  }

  @Test
  void location_isSentUnchanged() throws Exception {
    var emitter = open(List.of());
    var message = json(location(UUID.randomUUID()));

    locations.getValue().accept(message);

    assertThat(emitter.sent.get(1)).contains("event:location").contains(message);
  }

  @Test
  void ownMemberLeft_endsTheStream() throws Exception {
    var emitter = open(List.of());

    events.getValue().accept(json(AGFrameDto.memberLeft(memberId)));

    assertThat(emitter.completed).isTrue();
  }

  @Test
  void otherMemberLeft_keepsTheStream() throws Exception {
    var emitter = open(List.of());

    events.getValue().accept(json(AGFrameDto.memberLeft(UUID.randomUUID())));

    assertThat(emitter.completed).isFalse();
    assertThat(emitter.sent).hasSize(1);
  }

  @Test
  void groupDeleted_endsTheStream() throws Exception {
    var emitter = open(List.of());

    events.getValue().accept(json(AGFrameDto.groupDeleted()));

    assertThat(emitter.completed).isTrue();
  }

  @Test
  void otherEvents_areNotSent() throws Exception {
    var emitter = open(List.of());

    events.getValue().accept(json(AGFrameDto.circleDeleted(UUID.randomUUID())));

    assertThat(emitter.sent).hasSize(1);
    assertThat(emitter.completed).isFalse();
  }

  @Test
  void open_missingGroup_unsubscribesAndThrows() throws Exception {
    when(redisService.subscribe(eq("ag-" + groupId + "-locations"), any()))
        .thenReturn(locationsListener);
    when(redisService.subscribe(eq("ag-" + groupId + "-events"), any())).thenReturn(eventsListener);
    when(anonymousGroupService.getLocationSnapshot(groupId))
        .thenThrow(new AGNotFoundException(groupId));

    assertThatThrownBy(() -> streams.open(groupId, memberId, new RecordingEmitter()))
        .isInstanceOf(AGNotFoundException.class);
    verify(redisService).unsubscribe("ag-" + groupId + "-locations", locationsListener);
    verify(redisService).unsubscribe("ag-" + groupId + "-events", eventsListener);
  }

  @Test
  void close_unsubscribesFromBothChannels() throws Exception {
    open(List.of());

    streams.closeAll();

    verify(redisService).unsubscribe("ag-" + groupId + "-locations", locationsListener);
    verify(redisService).unsubscribe("ag-" + groupId + "-events", eventsListener);
  }

  @Test
  void sendHeartbeats_sendsACommentToEveryStream() throws Exception {
    var emitter = open(List.of());

    streams.sendHeartbeats();

    assertThat(emitter.sent).hasSize(2);
    assertThat(emitter.sent.get(1)).startsWith(":");
  }

  @Test
  void sendHeartbeats_brokenStream_isEnded() throws Exception {
    var emitter = open(List.of());
    emitter.failing = true;

    streams.sendHeartbeats();

    assertThat(emitter.completed).isTrue();
  }
}
