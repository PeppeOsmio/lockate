package com.peppeosmio.lockate.anonymous_group.controllers;

import com.peppeosmio.lockate.anonymous_group.dto.AGGetMembersCountDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGGetMembersResDto;
import com.peppeosmio.lockate.anonymous_group.dto.CircleUpsertReqDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGCircleConflictException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGCircleNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotAdminException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import java.util.UUID;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@SecuredAGMember
@Slf4j
@RestController
@RequestMapping("/api/anonymous-groups")
@Validated
public class SecuredAGController {
  private final AnonymousGroupService anonymousGroupService;

  public SecuredAGController(AnonymousGroupService anonymousGroupService) {
    this.anonymousGroupService = anonymousGroupService;
  }

  @GetMapping("/{anonymousGroupId}/members/auth/verify")
  @ResponseStatus(HttpStatus.OK)
  void verifyMemberAuth(
      @PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication) {}

  @GetMapping("/{anonymousGroupId}/members")
  @ResponseStatus(HttpStatus.OK)
  AGGetMembersResDto getAGMembers(
      @PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication)
      throws AGNotFoundException, UnauthorizedException {
    return anonymousGroupService.getMembers(
        anonymousGroupId, (AGMemberAuthentication) authentication);
  }

  @PostMapping("/{anonymousGroupId}/members/leave")
  @ResponseStatus(HttpStatus.OK)
  void memberLeave(@PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication)
      throws AGNotFoundException, UnauthorizedException {
    anonymousGroupService.deleteMember(anonymousGroupId, (AGMemberAuthentication) authentication);
  }

  @PostMapping("/{anonymousGroupId}/members/{agMemberId}/promote")
  @ResponseStatus(HttpStatus.OK)
  void promoteMember(
      @PathVariable UUID anonymousGroupId,
      @PathVariable UUID agMemberId,
      AGMemberAuthentication authentication)
      throws UnauthorizedException, AGMemberNotAdminException, AGMemberNotFoundException {
    anonymousGroupService.promoteMember(anonymousGroupId, authentication, agMemberId);
  }

  @GetMapping("/{anonymousGroupId}/members/count")
  @ResponseStatus(HttpStatus.OK)
  AGGetMembersCountDto getMembersCount(
      @PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication)
      throws UnauthorizedException, AGNotFoundException {
    return anonymousGroupService.getMembersCount(
        anonymousGroupId, (AGMemberAuthentication) authentication);
  }

  @DeleteMapping("/{anonymousGroupId}")
  @ResponseStatus(HttpStatus.OK)
  public void deleteAnonymousGroup(
      @PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication)
      throws UnauthorizedException, AGNotFoundException, AGMemberNotAdminException {
    anonymousGroupService.deleteAnonymousGroup(
        anonymousGroupId, (AGMemberAuthentication) authentication);
  }

  @PutMapping("/{anonymousGroupId}/circles/{circleId}")
  @ResponseStatus(HttpStatus.OK)
  public void upsertCircle(
      @PathVariable UUID anonymousGroupId,
      @PathVariable UUID circleId,
      @Valid @RequestBody CircleUpsertReqDto dto,
      AGMemberAuthentication authentication)
      throws AGNotFoundException, AGCircleConflictException {
    anonymousGroupService.upsertCircle(anonymousGroupId, circleId, dto.encryptedData());
  }

  @DeleteMapping("/{anonymousGroupId}/circles/{circleId}")
  @ResponseStatus(HttpStatus.OK)
  public void deleteCircle(
      @PathVariable UUID anonymousGroupId,
      @PathVariable UUID circleId,
      AGMemberAuthentication authentication)
      throws AGCircleNotFoundException {
    anonymousGroupService.deleteCircle(anonymousGroupId, circleId);
  }
}
