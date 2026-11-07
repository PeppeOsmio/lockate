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
import com.peppeosmio.lockate.anonymous_group.sse.AGLocationStreams;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SecuredAGMember
@Slf4j
@RestController
@RequestMapping("/api/anonymous-groups")
@Validated
public class SecuredAGController {
  private final AnonymousGroupService anonymousGroupService;
  private final AGLocationStreams agLocationStreams;

  public SecuredAGController(
      AnonymousGroupService anonymousGroupService, AGLocationStreams agLocationStreams) {
    this.anonymousGroupService = anonymousGroupService;
    this.agLocationStreams = agLocationStreams;
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
      throws AGNotFoundException, AGCircleConflictException, InvalidEncryptedDataException {
    anonymousGroupService.upsertCircle(anonymousGroupId, circleId, dto);
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

  @GetMapping(value = "/{anonymousGroupId}/locations", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamLocations(
      @PathVariable UUID anonymousGroupId, AGMemberAuthentication authentication)
      throws AGNotFoundException {
    return agLocationStreams.open(anonymousGroupId, authentication.getId(), new SseEmitter(0L));
  }
}
