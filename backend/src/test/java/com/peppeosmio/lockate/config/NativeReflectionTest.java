package com.peppeosmio.lockate.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.peppeosmio.lockate.anonymous_group.controllers.AnonymousGroupController;
import com.peppeosmio.lockate.anonymous_group.dto.AGJoinReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGJoinResDto;
import com.peppeosmio.lockate.anonymous_group.dto.AGLocationSaveReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.LocationRecordDto;
import com.peppeosmio.lockate.anonymous_group.dto.LocationUpdateDto;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import com.peppeosmio.lockate.invite.InviteController;
import com.peppeosmio.lockate.invite.InviteRecord;
import com.peppeosmio.lockate.invite.SecuredInviteController;
import com.peppeosmio.lockate.invite.dto.InviteClaimDto;
import com.peppeosmio.lockate.invite.dto.InviteCreateReqDto;
import com.peppeosmio.lockate.invite.dto.InviteCreateResDto;
import com.peppeosmio.lockate.invite.dto.InviteeMessageDto;
import com.peppeosmio.lockate.invite.dto.InviteeProgressDto;
import com.peppeosmio.lockate.invite.dto.InviterFinalDto;
import com.peppeosmio.lockate.invite.dto.InviterMessageDto;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.annotation.ReflectiveRuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class NativeReflectionTest {

  private final RuntimeHints hints = new RuntimeHints();

  private void register(Class<?>... types) {
    new ReflectiveRuntimeHintsRegistrar().registerRuntimeHints(hints, types);
  }

  @Test
  void locationUpdateDto_registersAccessors() {
    register(LocationUpdateDto.class);

    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(LocationUpdateDto.class, "location"))
        .accepts(hints);
    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(LocationUpdateDto.class, "memberId"))
        .accepts(hints);
  }

  @Test
  void locationUpdateDto_registersNestedTypesTransitively() {
    register(LocationUpdateDto.class);

    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(LocationRecordDto.class, "encryptedCoordinates"))
        .accepts(hints);
  }

  @Test
  void agLocationSaveReqDto_registersAccessors() {
    register(AGLocationSaveReqDto.class);

    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(AGLocationSaveReqDto.class, "encryptedLocation"))
        .accepts(hints);
  }

  @Test
  void inviteRecord_registersAccessors() {
    register(InviteRecord.class);

    assertThat(
            RuntimeHintsPredicates.reflection().onMethodInvocation(InviteRecord.class, "expiresAt"))
        .accepts(hints);
  }

  @Test
  void controllerDtos_areRegisteredThroughTheirControllers() {
    register(InviteController.class, SecuredInviteController.class, AnonymousGroupController.class);

    var reflection = RuntimeHintsPredicates.reflection();
    assertThat(reflection.onMethodInvocation(InviteCreateReqDto.class, "round1")).accepts(hints);
    assertThat(reflection.onMethodInvocation(InviteCreateResDto.class, "joinToken")).accepts(hints);
    assertThat(reflection.onMethodInvocation(InviteeMessageDto.class, "round2")).accepts(hints);
    assertThat(reflection.onMethodInvocation(InviteClaimDto.class, "claimToken")).accepts(hints);
    assertThat(reflection.onMethodInvocation(InviterMessageDto.class, "payload")).accepts(hints);
    assertThat(reflection.onMethodInvocation(InviteeProgressDto.class, "tb")).accepts(hints);
    assertThat(reflection.onMethodInvocation(InviterFinalDto.class, "payload")).accepts(hints);
    assertThat(reflection.onMethodInvocation(AGJoinReqDto.class, "joinToken")).accepts(hints);
    assertThat(reflection.onMethodInvocation(AGJoinResDto.class, "members")).accepts(hints);
    assertThat(reflection.onMethodInvocation(EncryptedDataDto.class, "cipherText")).accepts(hints);
    // Only the controllers' own types: what the service serializes itself needs its annotation
    assertThat(reflection.onMethodInvocation(InviteRecord.class, "expiresAt")).rejects(hints);
  }

  @Test
  void errorResponseDto_registersAccessors() {
    register(ErrorResponseDto.class);

    assertThat(
            RuntimeHintsPredicates.reflection().onMethodInvocation(ErrorResponseDto.class, "error"))
        .accepts(hints);
  }
}
