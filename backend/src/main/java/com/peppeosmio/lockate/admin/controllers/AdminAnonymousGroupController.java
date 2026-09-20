package com.peppeosmio.lockate.admin.controllers;

import com.peppeosmio.lockate.admin.security.SecuredAdmin;
import com.peppeosmio.lockate.anonymous_group.dto.AGAdminMemberDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGAdminSummaryDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@SecuredAdmin
@RestController
@RequestMapping("/api/admin/anonymous-groups")
public class AdminAnonymousGroupController {

  private final AnonymousGroupService anonymousGroupService;

  public AdminAnonymousGroupController(AnonymousGroupService anonymousGroupService) {
    this.anonymousGroupService = anonymousGroupService;
  }

  @GetMapping("")
  @ResponseStatus(HttpStatus.OK)
  List<AGAdminSummaryDto> listGroups() {
    return anonymousGroupService.listGroupsForAdmin();
  }

  @GetMapping("/{anonymousGroupId}/members")
  @ResponseStatus(HttpStatus.OK)
  List<AGAdminMemberDto> listMembers(@PathVariable UUID anonymousGroupId)
      throws AGNotFoundException {
    return anonymousGroupService.listMembersForAdmin(anonymousGroupId);
  }
}
