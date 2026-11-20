package com.peppeosmio.lockate.admin.service;

import com.peppeosmio.lockate.admin.dto.AdminGroupDto;
import com.peppeosmio.lockate.admin.dto.AdminGroupQueryDto;
import com.peppeosmio.lockate.admin.dto.AdminMemberDto;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository.Filter;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository.GroupRow;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository.MemberRow;
import com.peppeosmio.lockate.admin.repository.AdminGroupRepository.Sort;
import com.peppeosmio.lockate.anonymous_group.classes.AGMemberRef;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.location.AGLastLocationService;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import com.peppeosmio.lockate.common.dto.PageResponseDto;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * What the admin sees of groups and members: the database's counts and saved times, with who is
 * online and the positions not saved yet from Redis. Sorting and the inactivity filter use the
 * saved times, at most a save interval old.
 */
@Service
public class AdminGroupService {

  private final AdminGroupRepository repository;
  private final AGPresenceService presenceService;
  private final AGLastLocationService lastLocationService;

  public AdminGroupService(
      AdminGroupRepository repository,
      AGPresenceService presenceService,
      AGLastLocationService lastLocationService) {
    this.repository = repository;
    this.presenceService = presenceService;
    this.lastLocationService = lastLocationService;
  }

  public PageResponseDto<AdminGroupDto> list(AdminGroupQueryDto query) {
    var filter =
        new Filter(
            query.q() == null ? null : query.q().toLowerCase(Locale.ROOT),
            query.inactiveDays() == null
                ? null
                : LocalDateTime.now(ZoneOffset.UTC).minusDays(query.inactiveDays()),
            Boolean.TRUE.equals(query.noAdmin()));
    var total = repository.countGroups(filter);
    var rows =
        repository.findGroups(
            filter,
            Sort.valueOf(query.sort().toUpperCase(Locale.ROOT)),
            (long) query.page() * query.size(),
            query.size());
    return new PageResponseDto<>(
        withLiveData(rows),
        query.page(),
        query.size(),
        total,
        (int) ((total + query.size() - 1) / query.size()));
  }

  public AdminGroupDto get(UUID groupId) throws AGNotFoundException {
    var row = repository.findGroup(groupId).orElseThrow(() -> new AGNotFoundException(groupId));
    return withLiveData(List.of(row)).getFirst();
  }

  public List<AdminMemberDto> members(UUID groupId) throws AGNotFoundException {
    if (repository.findGroup(groupId).isEmpty()) {
      throw new AGNotFoundException(groupId);
    }
    var members = repository.findMembers(List.of(groupId));
    var live = live(members);
    return members.stream()
        .map(
            m ->
                new AdminMemberDto(
                    m.id(),
                    m.createdAt(),
                    m.isAdmin(),
                    live.online().contains(ref(m)),
                    m.pushRegistered(),
                    newest(m.lastLocationAt(), live.times().get(ref(m)))))
        .toList();
  }

  private List<AdminGroupDto> withLiveData(List<GroupRow> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    var members =
        repository.findMembers(rows.stream().map(GroupRow::id).toList()).stream()
            .collect(Collectors.groupingBy(MemberRow::groupId));
    var live = live(members.values().stream().flatMap(List::stream).toList());
    return rows.stream()
        .map(
            row -> {
              var groupMembers = members.getOrDefault(row.id(), List.of());
              var lastLocationAt = row.lastLocationAt();
              for (var m : groupMembers) {
                lastLocationAt = newest(lastLocationAt, live.times().get(ref(m)));
              }
              return new AdminGroupDto(
                  row.id(),
                  row.createdAt(),
                  row.memberCount(),
                  row.adminCount(),
                  groupMembers.stream().filter(m -> live.online().contains(ref(m))).count(),
                  row.pushRegisteredCount(),
                  row.circleCount(),
                  lastLocationAt);
            })
        .toList();
  }

  private record Live(Set<AGMemberRef> online, Map<AGMemberRef, LocalDateTime> times) {}

  private Live live(List<MemberRow> members) {
    var refs = members.stream().map(AdminGroupService::ref).toList();
    return new Live(presenceService.online(refs), lastLocationService.lastTimes(refs));
  }

  private static AGMemberRef ref(MemberRow member) {
    return new AGMemberRef(member.groupId(), member.id());
  }

  private static LocalDateTime newest(LocalDateTime a, LocalDateTime b) {
    if (a == null || b == null) {
      return a == null ? b : a;
    }
    return a.isAfter(b) ? a : b;
  }
}
