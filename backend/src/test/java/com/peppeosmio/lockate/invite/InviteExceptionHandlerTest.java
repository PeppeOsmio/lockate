package com.peppeosmio.lockate.invite;

import static org.assertj.core.api.Assertions.assertThat;

import com.peppeosmio.lockate.invite.exceptions.InvalidClaimTokenException;
import com.peppeosmio.lockate.invite.exceptions.InvalidInviteMessageException;
import com.peppeosmio.lockate.invite.exceptions.InvalidJoinTokenException;
import com.peppeosmio.lockate.invite.exceptions.InviteAlreadyClaimedException;
import com.peppeosmio.lockate.invite.exceptions.InviteConflictException;
import com.peppeosmio.lockate.invite.exceptions.InviteNotFoundException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteExceptionHandlerTest {

  private final InviteExceptionHandler handler = new InviteExceptionHandler();

  @Test
  void handleInviteNotFound_returns404() {
    var response = handler.handleInviteNotFound(new InviteNotFoundException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error()).isEqualTo("invite_not_found");
  }

  @Test
  void handleInviteAlreadyClaimed_returns409() {
    var response = handler.handleInviteAlreadyClaimed(new InviteAlreadyClaimedException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error()).isEqualTo("invite_already_claimed");
  }

  @Test
  void handleInviteConflict_returns409() {
    var response = handler.handleInviteConflict(new InviteConflictException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error()).isEqualTo("invite_conflict");
  }

  @Test
  void handleInvalidJoinToken_returns401() {
    var response = handler.handleInvalidJoinToken(new InvalidJoinTokenException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error()).isEqualTo("invalid_join_token");
  }

  @Test
  void handleInvalidClaimToken_returns401() {
    var response = handler.handleInvalidClaimToken(new InvalidClaimTokenException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error()).isEqualTo("invalid_claim_token");
  }

  @Test
  void handleTooManyRequests_returns429() {
    var response = handler.handleTooManyRequests(new TooManyRequestsException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getBody().error()).isEqualTo("too_many_requests");
  }

  @Test
  void handleInvalidInviteMessage_returns400() {
    var response = handler.handleInvalidInviteMessage(new InvalidInviteMessageException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error()).isEqualTo("invalid_invite_message");
  }
}
