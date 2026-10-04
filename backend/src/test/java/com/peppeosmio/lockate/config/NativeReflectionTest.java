package com.peppeosmio.lockate.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.peppeosmio.lockate.anonymous_group.dto.AGLocationSaveReqDto;
import com.peppeosmio.lockate.anonymous_group.dto.LocationRecordDto;
import com.peppeosmio.lockate.anonymous_group.dto.LocationUpdateDto;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import com.peppeosmio.lockate.srp.SrpSession;
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
  void srpSession_registersAccessors() {
    register(SrpSession.class);

    assertThat(
            RuntimeHintsPredicates.reflection().onMethodInvocation(SrpSession.class, "createdAt"))
        .accepts(hints);
  }

  @Test
  void errorResponseDto_registersAccessors() {
    register(ErrorResponseDto.class);

    assertThat(
            RuntimeHintsPredicates.reflection().onMethodInvocation(ErrorResponseDto.class, "error"))
        .accepts(hints);
  }
}
