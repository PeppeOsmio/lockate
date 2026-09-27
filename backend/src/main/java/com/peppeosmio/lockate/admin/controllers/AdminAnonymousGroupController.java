package com.peppeosmio.lockate.admin.controllers;

import com.peppeosmio.lockate.admin.security.SecuredAdmin;
import com.peppeosmio.lockate.anonymous_group.dto.AGAdminMemberDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGAdminSummaryDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.dto.PageResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  PageResponseDto<AGAdminSummaryDto> listGroups(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return anonymousGroupService.listGroupsForAdmin(
        PageRequest.of(page, size, Sort.by("createdAt").descending()));
  }

  @GetMapping("/{anonymousGroupId}/members")
  @ResponseStatus(HttpStatus.OK)
  List<AGAdminMemberDto> listMembers(@PathVariable UUID anonymousGroupId)
      throws AGNotFoundException {
    return anonymousGroupService.listMembersForAdmin(anonymousGroupId);
  }

  @DeleteMapping("/{anonymousGroupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteGroup(@PathVariable UUID anonymousGroupId) throws AGNotFoundException {
    anonymousGroupService.deleteAnonymousGroupForAdmin(anonymousGroupId);
  }

  @DeleteMapping("/{anonymousGroupId}/members/{agMemberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteMember(@PathVariable UUID anonymousGroupId, @PathVariable UUID agMemberId)
      throws AGMemberNotFoundException {
    anonymousGroupService.deleteMemberForAdmin(anonymousGroupId, agMemberId);
  }
}
