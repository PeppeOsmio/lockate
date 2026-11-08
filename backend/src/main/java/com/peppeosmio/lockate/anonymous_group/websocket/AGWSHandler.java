package com.peppeosmio.lockate.anonymous_group.websocket;

import com.peppeosmio.lockate.anonymous_group.dto.AGCircleEventReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGMemberMessageReqDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.redis.RedisService;
import com.peppeosmio.lockate.utils.TTLMap;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * The realtime channel of a group member: it receives the member's encrypted locations and circle
 * events and sends back the group's state and everything that happens in it. The other members'
 * locations go on the SSE stream instead, open only while the member looks at the map.
 */
@Slf4j
@Component
public class AGWSHandler extends TextWebSocketHandler {

  public static final CloseStatus MEMBER_REMOVED = new CloseStatus(4401, "member_removed");
  public static final CloseStatus GROUP_DELETED = new CloseStatus(4404, "group_deleted");
  public static final String MEMBER_LOCATION = "member.location";
  public static final String CIRCLE_EVENT = "circle.event";
  public static final String MEMBER_PAUSED = "member.paused";
  private static final int SEND_TIME_LIMIT_MS = 10_000;
  private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

  private final ObjectMapper objectMapper;
  private final AnonymousGroupService anonymousGroupService;
  private final RedisService redisService;
  private final AGPresenceService presenceService;
  private final TTLMap<UUID, LocalDateTime> lastSavedLocationsCache =
      new TTLMap<>(Duration.ofMinutes(5));
  private final Map<String, Connection> connections = new ConcurrentHashMap<>();

  public AGWSHandler(
      ObjectMapper objectMapper,
      AnonymousGroupService anonymousGroupService,
      RedisService redisService,
      AGPresenceService presenceService) {
    this.objectMapper = objectMapper;
    this.anonymousGroupService = anonymousGroupService;
    this.redisService = redisService;
    this.presenceService = presenceService;
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
    var connection = connections.remove(session.getId());
    if (connection != null) {
      redisService.unsubscribe(connection.channel, connection.listener);
    }
    log.info(
        "[WS] Disconnected: anonymousGroupId="
            + anonymousGroupId
            + " memberId="
            + authentication.getId()
            + " status="
            + status);
  }

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
    try {
      var sessionAttributes = session.getAttributes();
      var anonymousGroupId = (UUID) sessionAttributes.get("anonymousGroupId");
      if (!(sessionAttributes.get("authentication")
          instanceof AGMemberAuthentication agMemberAuthentication)) {
        throw new UnauthorizedException();
      }
      var tree = objectMapper.readTree(message.getPayload());
      switch (tree.path("type").asString("")) {
        case MEMBER_LOCATION -> {
          var dto = objectMapper.treeToValue(tree, AGMemberMessageReqDto.class);
          var lastSavedLocationTimestamp =
              lastSavedLocationsCache.get(agMemberAuthentication.getId()).orElse(null);
          var timestampSaved =
              anonymousGroupService
                  .relayMemberMessage(
                      anonymousGroupId, agMemberAuthentication, dto, lastSavedLocationTimestamp)
                  .orElse(null);
          if (timestampSaved != null) {
            lastSavedLocationsCache.put(agMemberAuthentication.getId(), timestampSaved);
          }
          presenceService.onLocation(anonymousGroupId, agMemberAuthentication.getId());
        }
        case MEMBER_PAUSED ->
            presenceService.pause(anonymousGroupId, agMemberAuthentication.getId());
        case CIRCLE_EVENT ->
            anonymousGroupService.relayCircleEvent(
                anonymousGroupId,
                agMemberAuthentication,
                objectMapper.treeToValue(tree, AGCircleEventReqDto.class));
        default -> throw new InvalidEncryptedDataException();
      }
    } catch (AGNotFoundException
        | UnauthorizedException
        | InvalidEncryptedDataException
        | JacksonException e) {
      session.close(CloseStatus.BAD_DATA);
    } catch (Exception e) {
      session.close(CloseStatus.SERVER_ERROR);
    }
  }
}
