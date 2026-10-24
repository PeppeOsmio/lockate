package com.peppeosmio.lockate.invite;

import com.peppeosmio.lockate.anonymous_group.controllers.SecuredAGMember;
import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.invite.dto.InviteCreateReqDto;
import com.peppeosmio.lockate.invite.dto.InviteCreateResDto;
import com.peppeosmio.lockate.invite.dto.InviteeProgressDto;
import com.peppeosmio.lockate.invite.dto.InviterConfirmDto;
import com.peppeosmio.lockate.invite.dto.InviterFinalDto;
import com.peppeosmio.lockate.invite.exceptions.InvalidInviteMessageException;
import com.peppeosmio.lockate.invite.exceptions.InviteConflictException;
import com.peppeosmio.lockate.invite.exceptions.InviteNotFoundException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@SecuredAGMember
@RestController
@RequestMapping("/api/anonymous-groups/{anonymousGroupId}/invites")
@Validated
public class SecuredInviteController {
  private final InviteService inviteService;

  public SecuredInviteController(InviteService inviteService) {
    this.inviteService = inviteService;
  }

  @PostMapping("")
  @ResponseStatus(HttpStatus.CREATED)
  InviteCreateResDto createInvite(
      @PathVariable UUID anonymousGroupId,
      @Valid @RequestBody InviteCreateReqDto dto,
      AGMemberAuthentication authentication)
      throws TooManyRequestsException, InvalidInviteMessageException, Base64Exception {
    return inviteService.createInvite(anonymousGroupId, authentication, dto);
  }

  @GetMapping("/{inviteId}")
  InviteeProgressDto getInviteeProgress(
      @PathVariable UUID anonymousGroupId,
      @PathVariable UUID inviteId,
      AGMemberAuthentication authentication)
      throws InviteNotFoundException {
    return inviteService.getInviteeProgress(anonymousGroupId, inviteId);
  }

  @PostMapping("/{inviteId}/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void confirmInvite(
      @PathVariable UUID anonymousGroupId,
      @PathVariable UUID inviteId,
      @Valid @RequestBody InviterConfirmDto dto,
      AGMemberAuthentication authentication)
      throws InviteNotFoundException,
          InviteConflictException,
          InvalidInviteMessageException,
          Base64Exception {
    inviteService.confirmInvite(anonymousGroupId, inviteId, dto);
  }

  @PostMapping("/{inviteId}/final")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void postFinal(
      @PathVariable UUID anonymousGroupId,
      @PathVariable UUID inviteId,
      @Valid @RequestBody InviterFinalDto dto,
      AGMemberAuthentication authentication)
      throws InviteNotFoundException,
          InviteConflictException,
          InvalidInviteMessageException,
          Base64Exception {
    inviteService.postFinal(anonymousGroupId, inviteId, dto);
  }

  @DeleteMapping("/{inviteId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancelInvite(
      @PathVariable UUID anonymousGroupId,
      @PathVariable UUID inviteId,
      AGMemberAuthentication authentication)
      throws InviteNotFoundException {
    inviteService.cancelInvite(anonymousGroupId, inviteId);
  }
}
