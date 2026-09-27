package com.peppeosmio.lockate.admin.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.dto.AGAdminMemberDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGAdminSummaryDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.dto.PageResponseDto;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminAnonymousGroupControllerTest {

  @Mock private AnonymousGroupService anonymousGroupService;

  private AdminAnonymousGroupController controller;

  @BeforeEach
  void setUp() {
    controller = new AdminAnonymousGroupController(anonymousGroupService);
  }

  @Test
  void listGroups_delegatesToServiceAndReturnsPage() {
    var dto = new AGAdminSummaryDto(UUID.randomUUID(), LocalDateTime.now(ZoneOffset.UTC), 2L, null);
    var page = new PageResponseDto<>(List.of(dto), 0, 20, 1L, 1);
    when(anonymousGroupService.listGroupsForAdmin(any(Pageable.class))).thenReturn(page);

    var result = controller.listGroups(0, 20);

    verify(anonymousGroupService).listGroupsForAdmin(any(Pageable.class));
    assertThat(result.items()).hasSize(1);
  }

  @Test
  void listMembers_delegatesToServiceAndReturnsList() throws Exception {
    var groupId = UUID.randomUUID();
    var dto = new AGAdminMemberDto(UUID.randomUUID(), LocalDateTime.now(ZoneOffset.UTC), false, null);
    when(anonymousGroupService.listMembersForAdmin(groupId)).thenReturn(List.of(dto));

    var result = controller.listMembers(groupId);

    verify(anonymousGroupService).listMembersForAdmin(groupId);
    assertThat(result).hasSize(1);
  }

  @Test
  void listMembers_missingGroup_propagatesAGNotFoundException() throws Exception {
    var groupId = UUID.randomUUID();
    when(anonymousGroupService.listMembersForAdmin(groupId))
        .thenThrow(new AGNotFoundException(groupId));

    assertThatThrownBy(() -> controller.listMembers(groupId))
        .isInstanceOf(AGNotFoundException.class);
  }

  @Test
  void deleteGroup_existingGroup_delegatesToService() throws Exception {
    var groupId = UUID.randomUUID();

    controller.deleteGroup(groupId);

    verify(anonymousGroupService).deleteAnonymousGroupForAdmin(groupId);
  }

  @Test
  void deleteGroup_missingGroup_propagatesAGNotFoundException() throws Exception {
    var groupId = UUID.randomUUID();
    doThrow(new AGNotFoundException(groupId))
        .when(anonymousGroupService)
        .deleteAnonymousGroupForAdmin(groupId);

    assertThatThrownBy(() -> controller.deleteGroup(groupId))
        .isInstanceOf(AGNotFoundException.class);
  }

  @Test
  void deleteMember_existingMember_delegatesToService() throws Exception {
    var groupId = UUID.randomUUID();
    var memberId = UUID.randomUUID();

    controller.deleteMember(groupId, memberId);

    verify(anonymousGroupService).deleteMemberForAdmin(groupId, memberId);
  }

  @Test
  void deleteMember_missingMember_propagatesAGMemberNotFoundException() throws Exception {
    var groupId = UUID.randomUUID();
    var memberId = UUID.randomUUID();
    doThrow(new AGMemberNotFoundException(memberId))
        .when(anonymousGroupService)
        .deleteMemberForAdmin(groupId, memberId);

    assertThatThrownBy(() -> controller.deleteMember(groupId, memberId))
        .isInstanceOf(AGMemberNotFoundException.class);
  }
}
