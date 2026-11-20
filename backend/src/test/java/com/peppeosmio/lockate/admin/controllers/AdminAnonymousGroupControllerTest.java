package com.peppeosmio.lockate.admin.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.admin.dto.AdminGroupDto;
import com.peppeosmio.lockate.admin.dto.AdminGroupQueryDto;
import com.peppeosmio.lockate.admin.dto.AdminMemberDto;
import com.peppeosmio.lockate.admin.service.AdminGroupService;
import com.peppeosmio.lockate.anonymous_group.dto.AGAdminMemberUpdateDto;
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

@ExtendWith(MockitoExtension.class)
class AdminAnonymousGroupControllerTest {

  @Mock private AnonymousGroupService anonymousGroupService;
  @Mock private AdminGroupService adminGroupService;

  private AdminAnonymousGroupController controller;

  @BeforeEach
  void setUp() {
    controller = new AdminAnonymousGroupController(anonymousGroupService, adminGroupService);
  }

  @Test
  void listGroups_delegatesToAdminGroupService() {
    var dto =
        new AdminGroupDto(
            UUID.randomUUID(), LocalDateTime.now(ZoneOffset.UTC), 2, 1, 0, 1, 0, null);
    var page = new PageResponseDto<>(List.of(dto), 0, 20, 1L, 1);
    var query = new AdminGroupQueryDto(0, 20, "449b", "members", 7);
    when(adminGroupService.list(query)).thenReturn(page);

    var result = controller.listGroups(query);

    assertThat(result.items()).containsExactly(dto);
  }

  @Test
  void getGroup_delegatesToAdminGroupService() throws Exception {
    var groupId = UUID.randomUUID();
    var dto = new AdminGroupDto(groupId, LocalDateTime.now(ZoneOffset.UTC), 2, 1, 0, 1, 0, null);
    when(adminGroupService.get(groupId)).thenReturn(dto);

    assertThat(controller.getGroup(groupId)).isEqualTo(dto);
  }

  @Test
  void getGroup_missingGroup_propagatesAGNotFoundException() throws Exception {
    var groupId = UUID.randomUUID();
    when(adminGroupService.get(groupId)).thenThrow(new AGNotFoundException(groupId));

    assertThatThrownBy(() -> controller.getGroup(groupId)).isInstanceOf(AGNotFoundException.class);
  }

  @Test
  void listMembers_delegatesToAdminGroupService() throws Exception {
    var groupId = UUID.randomUUID();
    var dto =
        new AdminMemberDto(
            UUID.randomUUID(), LocalDateTime.now(ZoneOffset.UTC), false, true, true, null);
    when(adminGroupService.members(groupId)).thenReturn(List.of(dto));

    assertThat(controller.listMembers(groupId)).containsExactly(dto);
  }

  @Test
  void listMembers_missingGroup_propagatesAGNotFoundException() throws Exception {
    var groupId = UUID.randomUUID();
    when(adminGroupService.members(groupId)).thenThrow(new AGNotFoundException(groupId));

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

  @Test
  void updateMember_delegatesToService() throws Exception {
    var groupId = UUID.randomUUID();
    var memberId = UUID.randomUUID();

    controller.updateMember(groupId, memberId, new AGAdminMemberUpdateDto(true));

    verify(anonymousGroupService).setMemberAdminForAdmin(groupId, memberId, true);
  }

  @Test
  void updateMember_missingMember_propagatesAGMemberNotFoundException() throws Exception {
    var groupId = UUID.randomUUID();
    var memberId = UUID.randomUUID();
    doThrow(new AGMemberNotFoundException(memberId))
        .when(anonymousGroupService)
        .setMemberAdminForAdmin(groupId, memberId, false);

    assertThatThrownBy(
            () -> controller.updateMember(groupId, memberId, new AGAdminMemberUpdateDto(false)))
        .isInstanceOf(AGMemberNotFoundException.class);
  }
}
