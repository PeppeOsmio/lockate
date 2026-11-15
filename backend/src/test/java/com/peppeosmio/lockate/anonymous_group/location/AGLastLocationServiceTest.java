package com.peppeosmio.lockate.anonymous_group.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.dto.LocationRecordDto;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AGLastLocationServiceTest {

  @Mock private RedisService redisService;
  @Mock private ObjectMapper objectMapper;
  @Mock private AGLocationConfigurationProperties agLocationConfigurationProperties;

  private AGLastLocationService service;

  private final UUID groupId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final LocationRecordDto record =
      new LocationRecordDto(
          new EncryptedDataDto("coords", "iv1"),
          new EncryptedDataDto("activity", "iv2"),
          LocalDateTime.of(2026, 11, 15, 12, 0));

  @BeforeEach
  void setUp() {
    service =
        new AGLastLocationService(redisService, objectMapper, agLocationConfigurationProperties);
  }

  private String key(UUID memberId) {
    return "ag-location:" + groupId + ":" + memberId;
  }

  @Test
  void put_keepsTheRecordForTheRetentionAndMarksItUnsaved() {
    when(agLocationConfigurationProperties.getDuration()).thenReturn(Duration.ofDays(7));
    when(objectMapper.writeValueAsString(record)).thenReturn("{record}");

    service.put(groupId, memberId, record);

    verify(redisService).saveValue(key(memberId), "{record}", Duration.ofDays(7));
    verify(redisService).addToSet("ag-unsaved-locations", groupId + ":" + memberId);
  }

  @Test
  void get_returnsOnlyTheMembersWithARecord() {
    var other = UUID.randomUUID();
    when(redisService.getValues(List.of(key(memberId), key(other))))
        .thenReturn(Arrays.asList("{record}", null));
    when(objectMapper.readValue("{record}", LocationRecordDto.class)).thenReturn(record);

    var result = service.get(groupId, List.of(memberId, other));

    assertThat(result).containsExactlyEntriesOf(java.util.Map.of(memberId, record));
  }

  @Test
  void takeUnsaved_returnsTheRecordsOfTheUnsavedMembersAndSkipsTheForgottenOnes() {
    var forgotten = UUID.randomUUID();
    when(redisService.popAllFromSet("ag-unsaved-locations"))
        .thenReturn(List.of(groupId + ":" + memberId, groupId + ":" + forgotten));
    when(redisService.getValue(key(memberId))).thenReturn(Optional.of("{record}"));
    when(redisService.getValue(key(forgotten))).thenReturn(Optional.empty());
    when(objectMapper.readValue("{record}", LocationRecordDto.class)).thenReturn(record);

    var result = service.takeUnsaved();

    assertThat(result).containsExactly(new AGLastLocationService.Unsaved(memberId, record));
  }

  @Test
  void forget_deletesTheRecord() {
    service.forget(groupId, memberId);

    verify(redisService).deleteValue(key(memberId));
  }
}
