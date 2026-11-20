package com.peppeosmio.lockate.admin.controllers;

import com.peppeosmio.lockate.admin.dto.AdminGroupDto;
import com.peppeosmio.lockate.admin.dto.AdminGroupQueryDto;
import com.peppeosmio.lockate.admin.dto.AdminMemberDto;
import com.peppeosmio.lockate.admin.security.SecuredAdmin;
import com.peppeosmio.lockate.admin.service.AdminGroupService;
import com.peppeosmio.lockate.anonymous_group.dto.AGAdminMemberUpdateDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.dto.PageResponseDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@SecuredAdmin
@RestController
@RequestMapping("/api/v1/admin/anonymous-groups")
public class AdminAnonymousGroupController {

  private final AnonymousGroupService anonymousGroupService;
  private final AdminGroupService adminGroupService;

  public AdminAnonymousGroupController(
      AnonymousGroupService anonymousGroupService, AdminGroupService adminGroupService) {
    this.anonymousGroupService = anonymousGroupService;
    this.adminGroupService = adminGroupService;
  }

  @GetMapping("")
  @ResponseStatus(HttpStatus.OK)
  PageResponseDto<AdminGroupDto> listGroups(@Valid @ModelAttribute AdminGroupQueryDto query) {
    return adminGroupService.list(query);
  }

  @GetMapping("/{anonymousGroupId}")
  @ResponseStatus(HttpStatus.OK)
  AdminGroupDto getGroup(@PathVariable UUID anonymousGroupId) throws AGNotFoundException {
    return adminGroupService.get(anonymousGroupId);
  }

  @GetMapping("/{anonymousGroupId}/members")
  @ResponseStatus(HttpStatus.OK)
  List<AdminMemberDto> listMembers(@PathVariable UUID anonymousGroupId) throws AGNotFoundException {
    return adminGroupService.members(anonymousGroupId);
  }

  @DeleteMapping("/{anonymousGroupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteGroup(@PathVariable UUID anonymousGroupId) throws AGNotFoundException {
    anonymousGroupService.deleteAnonymousGroupForAdmin(anonymousGroupId);
    log.info("[Admin] Deleted group {}", anonymousGroupId);
  }

  @DeleteMapping("/{anonymousGroupId}/members/{agMemberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteMember(@PathVariable UUID anonymousGroupId, @PathVariable UUID agMemberId)
      throws AGMemberNotFoundException {
    anonymousGroupService.deleteMemberForAdmin(anonymousGroupId, agMemberId);
    log.info("[Admin] Removed member {} from group {}", agMemberId, anonymousGroupId);
  }

  @PatchMapping("/{anonymousGroupId}/members/{agMemberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void updateMember(
      @PathVariable UUID anonymousGroupId,
      @PathVariable UUID agMemberId,
      @Valid @RequestBody AGAdminMemberUpdateDto body)
      throws AGMemberNotFoundException {
    anonymousGroupService.setMemberAdminForAdmin(anonymousGroupId, agMemberId, body.isAGAdmin());
    log.info(
        "[Admin] Set admin={} on member {} of group {}",
        body.isAGAdmin(),
        agMemberId,
        anonymousGroupId);
  }
}
