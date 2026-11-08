package com.peppeosmio.lockate.anonymous_group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/**
 * Everything the server sends on the group websocket, and what it relays on the group's Redis
 * channel. Only the fields of the frame's {@code type} are set.
 */
@RegisterReflectionForBinding
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AGFrameDto(
    String type,
    List<AGMemberDto> members,
    List<CircleDto> circles,
    AGMemberDto member,
    UUID memberId,
    CircleEventDto circleEvent,
    Boolean isAGAdmin,
    CircleDto circle,
    UUID circleId,
    Boolean online,
    LocalDateTime lastSeen) {

  public static final String SNAPSHOT = "snapshot";
  public static final String MEMBER_JOINED = "member.joined";
  public static final String MEMBER_LEFT = "member.left";
  public static final String MEMBER_UPDATED = "member.updated";
  public static final String CIRCLE_UPSERTED = "circle.upserted";
  public static final String CIRCLE_DELETED = "circle.deleted";
  public static final String CIRCLE_EVENT = "circle.event";
  public static final String MEMBER_PRESENCE = "member.presence";
  // Never sent to clients: it closes their sockets
  public static final String GROUP_DELETED = "group.deleted";

  public static AGFrameDto snapshot(List<AGMemberDto> members, List<CircleDto> circles) {
    return new AGFrameDto(
        SNAPSHOT, members, circles, null, null, null, null, null, null, null, null);
  }

  public static AGFrameDto memberJoined(AGMemberDto member) {
    return new AGFrameDto(
        MEMBER_JOINED, null, null, member, null, null, null, null, null, null, null);
  }

  public static AGFrameDto memberLeft(UUID memberId) {
    return new AGFrameDto(
        MEMBER_LEFT, null, null, null, memberId, null, null, null, null, null, null);
  }

  public static AGFrameDto memberUpdated(UUID memberId, boolean isAGAdmin) {
    return new AGFrameDto(
        MEMBER_UPDATED, null, null, null, memberId, null, isAGAdmin, null, null, null, null);
  }

  public static AGFrameDto circleUpserted(CircleDto circle) {
    return new AGFrameDto(
        CIRCLE_UPSERTED, null, null, null, null, null, null, circle, null, null, null);
  }

  public static AGFrameDto circleDeleted(UUID circleId) {
    return new AGFrameDto(
        CIRCLE_DELETED, null, null, null, null, null, null, null, circleId, null, null);
  }

  public static AGFrameDto circleEventOf(UUID memberId, CircleEventDto circleEvent) {
    return new AGFrameDto(
        CIRCLE_EVENT, null, null, null, memberId, circleEvent, null, null, null, null, null);
  }

  public static AGFrameDto memberPresence(UUID memberId, boolean online, LocalDateTime lastSeen) {
    return new AGFrameDto(
        MEMBER_PRESENCE, null, null, null, memberId, null, null, null, null, online, lastSeen);
  }

  public static AGFrameDto groupDeleted() {
    return new AGFrameDto(
        GROUP_DELETED, null, null, null, null, null, null, null, null, null, null);
  }
}
