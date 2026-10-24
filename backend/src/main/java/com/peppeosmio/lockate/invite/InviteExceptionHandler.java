package com.peppeosmio.lockate.invite;

import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import com.peppeosmio.lockate.invite.exceptions.InvalidClaimTokenException;
import com.peppeosmio.lockate.invite.exceptions.InvalidInviteMessageException;
import com.peppeosmio.lockate.invite.exceptions.InvalidJoinTokenException;
import com.peppeosmio.lockate.invite.exceptions.InviteAlreadyClaimedException;
import com.peppeosmio.lockate.invite.exceptions.InviteConflictException;
import com.peppeosmio.lockate.invite.exceptions.InviteNotFoundException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

// Must come before GlobalExceptionHandler, whose catch-all would otherwise win: advices with the
// same order are tried in scan order, and invite/ sorts after common/
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class InviteExceptionHandler {
  @ExceptionHandler(InviteNotFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleInviteNotFound(InviteNotFoundException e) {
    return error(HttpStatus.NOT_FOUND, "invite_not_found");
  }

  @ExceptionHandler(InviteAlreadyClaimedException.class)
  public ResponseEntity<ErrorResponseDto> handleInviteAlreadyClaimed(
      InviteAlreadyClaimedException e) {
    return error(HttpStatus.CONFLICT, "invite_already_claimed");
  }

  @ExceptionHandler(InviteConflictException.class)
  public ResponseEntity<ErrorResponseDto> handleInviteConflict(InviteConflictException e) {
    return error(HttpStatus.CONFLICT, "invite_conflict");
  }

  @ExceptionHandler(InvalidJoinTokenException.class)
  public ResponseEntity<ErrorResponseDto> handleInvalidJoinToken(InvalidJoinTokenException e) {
    return error(HttpStatus.UNAUTHORIZED, "invalid_join_token");
  }

  @ExceptionHandler(InvalidClaimTokenException.class)
  public ResponseEntity<ErrorResponseDto> handleInvalidClaimToken(InvalidClaimTokenException e) {
    return error(HttpStatus.UNAUTHORIZED, "invalid_claim_token");
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ErrorResponseDto> handleTooManyRequests(TooManyRequestsException e) {
    return error(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests");
  }

  @ExceptionHandler(InvalidInviteMessageException.class)
  public ResponseEntity<ErrorResponseDto> handleInvalidInviteMessage(
      InvalidInviteMessageException e) {
    return error(HttpStatus.BAD_REQUEST, "invalid_invite_message");
  }

  private static ResponseEntity<ErrorResponseDto> error(HttpStatus status, String code) {
    return ResponseEntity.status(status).body(new ErrorResponseDto(code));
  }
}
