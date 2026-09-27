package com.peppeosmio.lockate.admin.controllers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAnonymousGroupControllerTest {

  @Mock private AnonymousGroupService anonymousGroupService;

  private AdminAnonymousGroupController controller;

  @BeforeEach
  void setUp() {
    controller = new AdminAnonymousGroupController(anonymousGroupService);
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
