package com.peppeosmio.lockate.anonymous_group.sse;

import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.redis.RedisService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * A group as it happens, streamed to a member only while it looks at the group: a {@code snapshot}
 * event (members with their last saved record, circles), then a {@code location} event for each
 * member message and a {@code frame} event ({@link AGFrameDto}) for each change and circle event.
 * Our own circle events and sharing changes aren't sent back to us. The stream ends after the
 * {@code member.left} of our removal or the {@code group.deleted} frame.
 */
@Slf4j
@Component
public class AGStreams {
  private static final long HEARTBEAT_SECONDS = 15;

  private final ObjectMapper objectMapper;
  private final AnonymousGroupService anonymousGroupService;
  private final RedisService redisService;
  private final Set<Stream> streams = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();

  public AGStreams(
      ObjectMapper objectMapper,
      AnonymousGroupService anonymousGroupService,
      RedisService redisService) {
    this.objectMapper = objectMapper;
    this.anonymousGroupService = anonymousGroupService;
    this.redisService = redisService;
    heartbeats.scheduleAtFixedRate(
        this::sendHeartbeats, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
  }

  /**
   * What arrives while the snapshot is being built is held back, in order, and sent right after it,
   * so the client never applies anything before the snapshot.
   */
  private class Stream {
    final SseEmitter emitter;
    final UUID ownMemberId;
    final String locationsChannel;
    final String eventsChannel;
    final List<SseEmitter.SseEventBuilder> pending = new ArrayList<>();
    boolean snapshotSent = false;
    boolean endAfterSnapshot = false;
    boolean closed = false;
    MessageListener locationsListener;
    MessageListener eventsListener;

    Stream(SseEmitter emitter, UUID anonymousGroupId, UUID ownMemberId) {
      this.emitter = emitter;
      this.ownMemberId = ownMemberId;
      this.locationsChannel = AnonymousGroupService.getRedisAGLocationsChannel(anonymousGroupId);
      this.eventsChannel = AnonymousGroupService.getRedisAGEventsChannel(anonymousGroupId);
    }

    synchronized void onLocation(String message) {
      sendOrHold(SseEmitter.event().name("location").data(message));
    }

    synchronized void onSnapshot(String snapshot) {
      send(SseEmitter.event().name("snapshot").data(snapshot));
      snapshotSent = true;
      pending.forEach(this::send);
      pending.clear();
      if (endAfterSnapshot) {
        emitter.complete();
      }
    }

    synchronized void onEvent(String message) {
      AGFrameDto frame;
      try {
        frame = objectMapper.readValue(message, AGFrameDto.class);
      } catch (JacksonException e) {
        log.warn("[SSE] Can't read frame: " + e.getMessage());
        return;
      }
      var aboutUs = ownMemberId.equals(frame.memberId());
      var type = frame.type();
      if (aboutUs
          && (AGFrameDto.CIRCLE_EVENT.equals(type) || AGFrameDto.MEMBER_PRESENCE.equals(type))) {
        return;
      }
      sendOrHold(SseEmitter.event().name("frame").data(message));
      if (AGFrameDto.GROUP_DELETED.equals(type)
          || (AGFrameDto.MEMBER_LEFT.equals(type) && aboutUs)) {
        if (snapshotSent) {
          emitter.complete();
        } else {
          endAfterSnapshot = true;
        }
      }
    }

    private void sendOrHold(SseEmitter.SseEventBuilder event) {
      if (snapshotSent) {
        send(event);
      } else {
        pending.add(event);
      }
    }

    void send(SseEmitter.SseEventBuilder event) {
      try {
        emitter.send(event);
      } catch (IOException | IllegalStateException e) {
        emitter.complete();
      }
    }

    synchronized void close() {
      if (closed) {
        return;
      }
      closed = true;
      streams.remove(this);
      if (locationsListener != null) {
        redisService.unsubscribe(locationsChannel, locationsListener);
      }
      if (eventsListener != null) {
        redisService.unsubscribe(eventsChannel, eventsListener);
      }
    }
  }

  public SseEmitter open(UUID anonymousGroupId, UUID memberId, SseEmitter emitter)
      throws AGNotFoundException {
    var stream = new Stream(emitter, anonymousGroupId, memberId);
    emitter.onCompletion(stream::close);
    emitter.onTimeout(stream::close);
    emitter.onError(e -> stream.close());
    streams.add(stream);
    stream.locationsListener = redisService.subscribe(stream.locationsChannel, stream::onLocation);
    stream.eventsListener = redisService.subscribe(stream.eventsChannel, stream::onEvent);
    try {
      var snapshot = anonymousGroupService.getSnapshot(anonymousGroupId);
      stream.onSnapshot(objectMapper.writeValueAsString(snapshot));
    } catch (AGNotFoundException e) {
      stream.close();
      throw e;
    }
    return emitter;
  }

  void sendHeartbeats() {
    streams.forEach(stream -> stream.send(SseEmitter.event().comment("heartbeat")));
  }

  void closeAll() {
    streams.forEach(Stream::close);
  }

  @PreDestroy
  public void shutdown() {
    heartbeats.shutdownNow();
    closeAll();
  }
}
