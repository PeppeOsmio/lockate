package com.peppeosmio.lockate.anonymous_group.websocket;

import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.redis.RedisService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The realtime channel of a group member: it sends the group's state and everything that happens in
 * it. The member sends only pings: its locations, circle events and pause are posted over REST
 * ({@code AGMemberMessageService}). The other members' locations go on the SSE stream instead, open
 * only while the member looks at the map.
 *
 * <p>The client pings once a minute, so a socket that has sent nothing for {@link
 * #IDLE_TIMEOUT_MILLIS} belongs to a phone that died or lost the network without closing it.
 */
@Slf4j
@Component
public class AGWSHandler extends TextWebSocketHandler {

  public static final CloseStatus MEMBER_REMOVED = new CloseStatus(4401, "member_removed");
  public static final CloseStatus GROUP_DELETED = new CloseStatus(4404, "group_deleted");
  public static final String PING = "ping";
  // 2.5 ping intervals, so one lost ping doesn't close a live socket
  static final long IDLE_TIMEOUT_MILLIS = 150_000;
  private static final long IDLE_CHECK_SECONDS = 30;
  private static final int SEND_TIME_LIMIT_MS = 10_000;
  private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

  private final ObjectMapper objectMapper;
  private final AnonymousGroupService anonymousGroupService;
  private final RedisService redisService;
  private final Map<String, Connection> connections = new ConcurrentHashMap<>();
  private final ScheduledExecutorService idleChecks = Executors.newSingleThreadScheduledExecutor();
  LongSupplier now = System::currentTimeMillis;

  public AGWSHandler(
      ObjectMapper objectMapper,
      AnonymousGroupService anonymousGroupService,
      RedisService redisService) {
    this.objectMapper = objectMapper;
    this.anonymousGroupService = anonymousGroupService;
    this.redisService = redisService;
    idleChecks.scheduleAtFixedRate(
        this::closeIdleConnections, IDLE_CHECK_SECONDS, IDLE_CHECK_SECONDS, TimeUnit.SECONDS);
  }

  /**
   * Frames from Redis that arrive while the snapshot is being built are held back and sent right
   * after it, so the client never misses a change nor applies it before the snapshot.
   */
  private class Connection {
    final WebSocketSession session;
    final UUID ownMemberId;
    final String channel;
    final List<String> pending = new ArrayList<>();
    boolean snapshotSent = false;
    MessageListener listener;
    volatile long lastReceivedMillis = now.getAsLong();

    Connection(WebSocketSession session, UUID ownMemberId, String channel) {
      this.session = session;
      this.ownMemberId = ownMemberId;
      this.channel = channel;
    }

    synchronized void onRedisMessage(String message) {
      if (snapshotSent) {
        forward(session, ownMemberId, message);
      } else {
        pending.add(message);
      }
    }

    // Forwarding under the lock keeps the held frames ahead of the ones that arrive meanwhile
    synchronized void onSnapshotSent() {
      snapshotSent = true;
      pending.forEach(message -> forward(session, ownMemberId, message));
      pending.clear();
    }
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
    var sessionAttributes = wsSession.getAttributes();
    var anonymousGroupId = (UUID) sessionAttributes.get("anonymousGroupId");
    if (!(sessionAttributes.get("authentication")
        instanceof AGMemberAuthentication authentication)) {
      throw new UnauthorizedException();
    }
    log.info(
        "[WS] Connected: anonymousGroupId="
            + anonymousGroupId
            + " memberId="
            + authentication.getId());
    var session =
        new ConcurrentWebSocketSessionDecorator(
            wsSession, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
    var channel = AnonymousGroupService.getRedisAGEventsChannel(anonymousGroupId);
    var connection = new Connection(session, authentication.getId(), channel);
    connection.listener = redisService.subscribe(channel, connection::onRedisMessage);
    connections.put(wsSession.getId(), connection);
    try {
      var snapshot = anonymousGroupService.getSnapshot(anonymousGroupId);
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(snapshot)));
    } catch (AGNotFoundException e) {
      session.close(GROUP_DELETED);
      return;
    }
    connection.onSnapshotSent();
  }

  private void forward(WebSocketSession session, UUID ownMemberId, String message) {
    try {
      var frame = objectMapper.readValue(message, AGFrameDto.class);
      switch (frame.type()) {
        case AGFrameDto.GROUP_DELETED -> session.close(GROUP_DELETED);
        case AGFrameDto.MEMBER_LEFT -> {
          if (ownMemberId.equals(frame.memberId())) {
            session.close(MEMBER_REMOVED);
          } else {
            session.sendMessage(new TextMessage(message));
          }
        }
        case AGFrameDto.CIRCLE_EVENT, AGFrameDto.MEMBER_PRESENCE -> {
          if (!ownMemberId.equals(frame.memberId())) {
            session.sendMessage(new TextMessage(message));
          }
        }
        default -> session.sendMessage(new TextMessage(message));
      }
    } catch (JacksonException | IOException e) {
      log.warn("[WS] Can't forward frame: " + e.getMessage());
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    var sessionAttributes = session.getAttributes();
    var anonymousGroupId = (UUID) sessionAttributes.get("anonymousGroupId");
    var authentication = (AGMemberAuthentication) sessionAttributes.get("authentication");
    release(session.getId());
    log.info(
        "[WS] Disconnected: anonymousGroupId="
            + anonymousGroupId
            + " memberId="
            + authentication.getId()
            + " status="
            + status);
  }

  private void release(String sessionId) {
    var connection = connections.remove(sessionId);
    if (connection != null) {
      redisService.unsubscribe(connection.channel, connection.listener);
    }
  }

  // Released before closing: the close of a dead socket may fail and never reach
  // afterConnectionClosed
  void closeIdleConnections() {
    var deadline = now.getAsLong() - IDLE_TIMEOUT_MILLIS;
    connections.forEach(
        (sessionId, connection) -> {
          if (connection.lastReceivedMillis >= deadline) {
            return;
          }
          try {
            release(sessionId);
            connection.session.close(CloseStatus.SESSION_NOT_RELIABLE);
          } catch (Exception e) {
            log.warn("[WS] Can't close idle socket: " + e.getMessage());
          }
        });
  }

  @PreDestroy
  public void shutdown() {
    idleChecks.shutdownNow();
  }

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
    var connection = connections.get(session.getId());
    if (connection != null) {
      connection.lastReceivedMillis = now.getAsLong();
    }
    try {
      if (!(session.getAttributes().get("authentication") instanceof AGMemberAuthentication)) {
        throw new UnauthorizedException();
      }
      var tree = objectMapper.readTree(message.getPayload());
      if (!PING.equals(tree.path("type").asString(""))) {
        throw new InvalidEncryptedDataException();
      }
      connection.session.sendMessage(
          new TextMessage(objectMapper.writeValueAsString(AGFrameDto.pong())));
    } catch (UnauthorizedException | InvalidEncryptedDataException | JacksonException e) {
      session.close(CloseStatus.BAD_DATA);
    } catch (Exception e) {
      session.close(CloseStatus.SERVER_ERROR);
    }
  }
}
