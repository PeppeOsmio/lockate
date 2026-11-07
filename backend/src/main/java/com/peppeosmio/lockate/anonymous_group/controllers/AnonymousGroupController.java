package com.peppeosmio.lockate.anonymous_group.controllers;

import com.peppeosmio.lockate.anonymous_group.dto.*;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGIdTakenException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.invite.exceptions.InvalidJoinTokenException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/anonymous-groups")
@Validated
public class AnonymousGroupController {

  private final AnonymousGroupService anonymousGroupService;

  public AnonymousGroupController(AnonymousGroupService anonymousGroupService) {
    this.anonymousGroupService = anonymousGroupService;
  }

  @PostMapping("")
  @ResponseStatus(HttpStatus.CREATED)
  AGCreateResDto createAnonymousGroup(@Valid @RequestBody AGCreateReqDto dto)
      throws InvalidEncryptedDataException, AGIdTakenException {
    return anonymousGroupService.createAnonymousGroup(dto);
  }

  @PostMapping("/{anonymousGroupId}/members/join")
  @ResponseStatus(HttpStatus.OK)
  AGJoinResDto joinWithInvite(
      @PathVariable UUID anonymousGroupId, @Valid @RequestBody AGJoinReqDto dto)
      throws AGNotFoundException,
          InvalidJoinTokenException,
          InvalidEncryptedDataException,
          AGIdTakenException {
    return anonymousGroupService.joinWithInvite(anonymousGroupId, dto);
  }
}
