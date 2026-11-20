package com.peppeosmio.lockate.anonymous_group.location;

import com.peppeosmio.lockate.anonymous_group.classes.AGMemberRef;
import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.dto.LocationRecordDto;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Every member's last position, kept in Redis so a snapshot gives it at once. {@code
 * LocationSaveJob} saves the last one of each member that sent since its previous run to the
 * database, every save interval, so the last position of a burst is always saved.
 */
@Service
public class AGLastLocationService {
  static final String UNSAVED_KEY = "ag-unsaved-locations";

  public record Unsaved(UUID memberId, LocationRecordDto record) {}

  private final RedisService redisService;
  private final ObjectMapper objectMapper;
  private final AGLocationConfigurationProperties agLocationConfigurationProperties;

  public AGLastLocationService(
      RedisService redisService,
      ObjectMapper objectMapper,
      AGLocationConfigurationProperties agLocationConfigurationProperties) {
    this.redisService = redisService;
    this.objectMapper = objectMapper;
    this.agLocationConfigurationProperties = agLocationConfigurationProperties;
  }

  private static String key(UUID anonymousGroupId, UUID memberId) {
    return "ag-location:" + anonymousGroupId + ":" + memberId;
  }

  public void put(UUID anonymousGroupId, UUID memberId, LocationRecordDto record) {
    redisService.saveValue(
        key(anonymousGroupId, memberId),
        objectMapper.writeValueAsString(record),
        agLocationConfigurationProperties.getDuration());
    redisService.addToSet(UNSAVED_KEY, anonymousGroupId + ":" + memberId);
  }

  /** The last position of each of the members that has one */
  public Map<UUID, LocationRecordDto> get(UUID anonymousGroupId, List<UUID> memberIds) {
    var values =
        redisService.getValues(memberIds.stream().map(id -> key(anonymousGroupId, id)).toList());
    var records = new HashMap<UUID, LocationRecordDto>();
    for (int i = 0; i < memberIds.size(); i++) {
      if (values.get(i) != null) {
        records.put(
            memberIds.get(i), objectMapper.readValue(values.get(i), LocationRecordDto.class));
      }
    }
    return records;
  }

  /** When each of the members that has a last position sent it, across groups */
  public Map<AGMemberRef, LocalDateTime> lastTimes(List<AGMemberRef> members) {
    if (members.isEmpty()) {
      return Map.of();
    }
    var values =
        redisService.getValues(members.stream().map(m -> key(m.groupId(), m.memberId())).toList());
    var times = new HashMap<AGMemberRef, LocalDateTime>();
    for (int i = 0; i < members.size(); i++) {
      if (values.get(i) != null) {
        times.put(
            members.get(i),
            objectMapper.readValue(values.get(i), LocationRecordDto.class).timestamp());
      }
    }
    return times;
  }

  /** The members that sent since the last call, on any instance, with their last position */
  public List<Unsaved> takeUnsaved() {
    var unsaved = new ArrayList<Unsaved>();
    for (var entry : redisService.popAllFromSet(UNSAVED_KEY)) {
      var ids = entry.split(":");
      var memberId = UUID.fromString(ids[1]);
      redisService
          .getValue(key(UUID.fromString(ids[0]), memberId))
          .ifPresent(
              value ->
                  unsaved.add(
                      new Unsaved(
                          memberId, objectMapper.readValue(value, LocationRecordDto.class))));
    }
    return unsaved;
  }

  /** The member left the group */
  public void forget(UUID anonymousGroupId, UUID memberId) {
    redisService.deleteValue(key(anonymousGroupId, memberId));
  }
}
