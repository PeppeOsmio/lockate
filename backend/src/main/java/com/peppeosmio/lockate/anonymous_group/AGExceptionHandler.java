package com.peppeosmio.lockate.anonymous_group;

import com.peppeosmio.lockate.anonymous_group.dto.AGPostLocationResDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGIdTakenException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotAdminException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGPlaceConflictException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGPlaceNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.anonymous_group.exceptions.PlacesChangedException;
import com.peppeosmio.lockate.anonymous_group.push.InvalidPushRegistrationException;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class AGExceptionHandler {
  @ExceptionHandler(Base64Exception.class)
  public ResponseEntity<ErrorResponseDto> handleBase64(Base64Exception e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponseDto("invalid_base64"));
  }

  @ExceptionHandler(AGNotFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleAGNotFound(AGNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDto("ag_not_found"));
  }

  @ExceptionHandler(AGMemberNotFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleAGMemberNotFound(AGMemberNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponseDto("ag_member_not_found"));
  }

  @ExceptionHandler(AGMemberNotAdminException.class)
  public ResponseEntity<ErrorResponseDto> handleAGMemberNotAdmin(AGMemberNotAdminException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponseDto("ag_member_not_admin"));
  }

  @ExceptionHandler(AGPlaceNotFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleAGPlaceNotFound(AGPlaceNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponseDto("ag_place_not_found"));
  }

  @ExceptionHandler(AGPlaceConflictException.class)
  public ResponseEntity<ErrorResponseDto> handleAGPlaceConflict(AGPlaceConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDto(e.getMessage()));
  }

  @ExceptionHandler(InvalidPushRegistrationException.class)
  public ResponseEntity<ErrorResponseDto> handleInvalidPushRegistration(
      InvalidPushRegistrationException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponseDto("invalid_push_registration"));
  }

  /** The phone fetches the places and computes the events again: the body is the version now. */
  @ExceptionHandler(PlacesChangedException.class)
  public ResponseEntity<AGPostLocationResDto> handlePlacesChanged(PlacesChangedException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new AGPostLocationResDto(e.getPlacesVersion()));
  }

  @ExceptionHandler(AGIdTakenException.class)
  public ResponseEntity<ErrorResponseDto> handleAGIdTaken(AGIdTakenException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDto(e.getMessage()));
  }
}
