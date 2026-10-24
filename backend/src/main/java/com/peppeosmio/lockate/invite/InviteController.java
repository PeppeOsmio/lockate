package com.peppeosmio.lockate.invite;

import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.invite.dto.InviteClaimDto;
import com.peppeosmio.lockate.invite.dto.InviteeConfirmDto;
import com.peppeosmio.lockate.invite.dto.InviteeMessageDto;
import com.peppeosmio.lockate.invite.dto.InviterHelloDto;
import com.peppeosmio.lockate.invite.dto.InviterMessageDto;
import com.peppeosmio.lockate.invite.exceptions.InvalidClaimTokenException;
import com.peppeosmio.lockate.invite.exceptions.InvalidInviteMessageException;
import com.peppeosmio.lockate.invite.exceptions.InviteAlreadyClaimedException;
import com.peppeosmio.lockate.invite.exceptions.InviteConflictException;
import com.peppeosmio.lockate.invite.exceptions.InviteNotFoundException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * The invitee's side of the exchange: it isn't a member yet, so it only knows the nameplate, and
 * proves it is the one who answered the invite with the claim token it got back.
 */
@RestController
@RequestMapping("/api/invites/{nameplate}")
@Validated
public class InviteController {
  private static final String CLAIM_TOKEN_HEADER = "X-Invite-Claim";

  private final InviteService inviteService;

  public InviteController(InviteService inviteService) {
    this.inviteService = inviteService;
  }

  @GetMapping("")
  InviterHelloDto getInviterHello(@PathVariable int nameplate, HttpServletRequest request)
      throws TooManyRequestsException, InviteNotFoundException {
    return inviteService.getInviterHello(nameplate, request.getRemoteAddr());
  }

  @PostMapping("/invitee-message")
  @ResponseStatus(HttpStatus.CREATED)
  InviteClaimDto postInviteeMessage(
      @PathVariable int nameplate,
      @Valid @RequestBody InviteeMessageDto dto,
      HttpServletRequest request)
      throws TooManyRequestsException,
          InviteNotFoundException,
          InviteAlreadyClaimedException,
          InvalidInviteMessageException,
          Base64Exception {
    return new InviteClaimDto(
        inviteService.postInviteeMessage(nameplate, request.getRemoteAddr(), dto));
  }

  @GetMapping("/inviter-message")
  InviterMessageDto getInviterMessage(
      @PathVariable int nameplate,
      @RequestHeader(value = CLAIM_TOKEN_HEADER, required = false) String claimToken,
      HttpServletRequest request)
      throws TooManyRequestsException, InviteNotFoundException, InvalidClaimTokenException {
    return inviteService.getInviterMessage(nameplate, request.getRemoteAddr(), claimToken);
  }

  @PostMapping("/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void postInviteeConfirmation(
      @PathVariable int nameplate,
      @RequestHeader(value = CLAIM_TOKEN_HEADER, required = false) String claimToken,
      @Valid @RequestBody InviteeConfirmDto dto,
      HttpServletRequest request)
      throws TooManyRequestsException,
          InviteNotFoundException,
          InvalidClaimTokenException,
          InviteConflictException,
          InvalidInviteMessageException,
          Base64Exception {
    inviteService.postInviteeConfirmation(nameplate, request.getRemoteAddr(), claimToken, dto);
  }
}
