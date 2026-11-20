package com.peppeosmio.lockate.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.admin.dto.AdminGroupQueryDto;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository.Filter;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository.GroupRow;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository.MemberRow;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository.Sort;
import com.peppeosmio.lockate.anonymous_group.classes.AGMemberRef;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.location.AGLastLocationService;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminGroupServiceTest {

  @Mock private AdminGroupRepository repository;
  @Mock private AGPresenceService presenceService;
  @Mock private AGLastLocationService lastLocationService;

  private AdminGroupService service;

  private final UUID groupId = UUID.randomUUID();
  private final LocalDateTime created = LocalDateTime.of(2026, 11, 1, 10, 0);
  private final LocalDateTime saved = LocalDateTime.of(2026, 11, 20, 10, 0);

  @BeforeEach
  void setUp() {
    service = new AdminGroupService(repository, presenceService, lastLocationService);
  }

  private GroupRow group(UUID id, LocalDateTime lastLocationAt) {
    return new GroupRow(id, created, 2, 1, 1, 3, lastLocationAt);
  }

  private MemberRow member(UUID id, UUID groupId, LocalDateTime lastLocationAt) {
    return new MemberRow(id, groupId, created, false, true, lastLocationAt);
  }

  @Test
  void list_mergesLiveDataIntoTheSavedOnes() {
    var memberA = UUID.randomUUID();
    var memberB = UUID.randomUUID();
    var refA = new AGMemberRef(groupId, memberA);
    var refB = new AGMemberRef(groupId, memberB);
    var live = saved.plusMinutes(1);
    when(repository.countGroups(any())).thenReturn(1L);
    when(repository.findGroups(any(), any(), anyLong(), anyInt()))
        .thenReturn(List.of(group(groupId, saved)));
    when(repository.findMembers(List.of(groupId)))
        .thenReturn(List.of(member(memberA, groupId, saved), member(memberB, groupId, null)));
    when(presenceService.online(List.of(refA, refB))).thenReturn(Set.of(refA));
    when(lastLocationService.lastTimes(List.of(refA, refB))).thenReturn(Map.of(refA, live));

    var result = service.list(new AdminGroupQueryDto(null, null, null, null, null, null));

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.totalPages()).isEqualTo(1);
    var dto = result.items().getFirst();
    assertThat(dto.memberCount()).isEqualTo(2);
    assertThat(dto.adminCount()).isEqualTo(1);
    assertThat(dto.pushRegisteredCount()).isEqualTo(1);
    assertThat(dto.circleCount()).isEqualTo(3);
    assertThat(dto.onlineCount()).isEqualTo(1);
    assertThat(dto.lastLocationAt()).isEqualTo(live);
  }

  @Test
  void list_keepsTheSavedTimeWhenRedisHasNothingNewer() {
    var memberA = UUID.randomUUID();
    var refA = new AGMemberRef(groupId, memberA);
    when(repository.countGroups(any())).thenReturn(1L);
    when(repository.findGroups(any(), any(), anyLong(), anyInt()))
        .thenReturn(List.of(group(groupId, saved)));
    when(repository.findMembers(List.of(groupId)))
        .thenReturn(List.of(member(memberA, groupId, saved)));
    when(presenceService.online(List.of(refA))).thenReturn(Set.of());
    when(lastLocationService.lastTimes(List.of(refA))).thenReturn(Map.of());

    var dto =
        service.list(new AdminGroupQueryDto(null, null, null, null, null, null)).items().getFirst();

    assertThat(dto.lastLocationAt()).isEqualTo(saved);
    assertThat(dto.onlineCount()).isZero();
  }

  @Test
  void list_passesSearchSortPagingAndInactivityToTheQuery() {
    when(repository.countGroups(any())).thenReturn(45L);
    when(repository.findGroups(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

    var result = service.list(new AdminGroupQueryDto(2, 20, "449BFE1B", "activity", 7, true));

    var filter = ArgumentCaptor.forClass(Filter.class);
    verify(repository)
        .findGroups(
            filter.capture(),
            org.mockito.Mockito.eq(Sort.ACTIVITY),
            org.mockito.Mockito.eq(40L),
            org.mockito.Mockito.eq(20));
    assertThat(filter.getValue().idPrefix()).isEqualTo("449bfe1b");
    assertThat(filter.getValue().noAdmin()).isTrue();
    assertThat(filter.getValue().inactiveSince())
        .isCloseTo(
            LocalDateTime.now(ZoneOffset.UTC).minusDays(7),
            org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
    assertThat(result.totalPages()).isEqualTo(3);
    assertThat(result.page()).isEqualTo(2);
    verify(repository, never()).findMembers(any());
  }

  @Test
  void get_missingGroup_throwsAGNotFoundException() {
    when(repository.findGroup(groupId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(groupId)).isInstanceOf(AGNotFoundException.class);
  }

  @Test
  void members_giveOnlineAndTheNewestPositionTime() throws Exception {
    var memberA = UUID.randomUUID();
    var memberB = UUID.randomUUID();
    var refA = new AGMemberRef(groupId, memberA);
    var refB = new AGMemberRef(groupId, memberB);
    var live = saved.plusMinutes(1);
    when(repository.findGroup(groupId)).thenReturn(Optional.of(group(groupId, saved)));
    when(repository.findMembers(List.of(groupId)))
        .thenReturn(List.of(member(memberA, groupId, saved), member(memberB, groupId, null)));
    when(presenceService.online(List.of(refA, refB))).thenReturn(Set.of(refA));
    when(lastLocationService.lastTimes(List.of(refA, refB))).thenReturn(Map.of(refA, live));

    var result = service.members(groupId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).online()).isTrue();
    assertThat(result.get(0).pushRegistered()).isTrue();
    assertThat(result.get(0).lastLocationAt()).isEqualTo(live);
    assertThat(result.get(1).online()).isFalse();
    assertThat(result.get(1).lastLocationAt()).isNull();
  }

  @Test
  void members_missingGroup_throwsAGNotFoundException() {
    when(repository.findGroup(groupId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.members(groupId)).isInstanceOf(AGNotFoundException.class);
    verify(repository, never()).findMembers(any());
  }
}
