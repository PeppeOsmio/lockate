package com.peppeosmio.lockate.anonymous_group.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AGMemberAuthenticatorTest {

  @Mock private AnonymousGroupService anonymousGroupService;

  private AGMemberAuthenticator authenticator;

  private final UUID anonymousGroupId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    authenticator = new AGMemberAuthenticator(anonymousGroupService);
  }

  @Test
  void authenticate_validHeader_delegatesToService() throws Exception {
    var memberId = UUID.randomUUID();
    var expected = new AGMemberAuthentication(memberId);
    when(anonymousGroupService.authenticateMember(anonymousGroupId, memberId, "sometoken"))
        .thenReturn(expected);

    var result =
        authenticator.authenticate(anonymousGroupId, "AGMember " + memberId + " sometoken");

    assertThat(result).isSameAs(expected);
  }

  @Test
  void authenticate_nullHeader_throwsUnauthorized() {
    assertThatThrownBy(() -> authenticator.authenticate(anonymousGroupId, null))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void authenticate_wrongPrefix_throwsUnauthorized() {
    assertThatThrownBy(
            () ->
                authenticator.authenticate(
                    anonymousGroupId, "Bearer " + UUID.randomUUID() + " token"))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void authenticate_malformedUuid_throwsUnauthorizedNotIllegalArgument() {
    assertThatThrownBy(
            () -> authenticator.authenticate(anonymousGroupId, "AGMember not-a-uuid sometoken"))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void authenticate_missingTokenSegment_throwsUnauthorizedNotArrayIndexOutOfBounds() {
    assertThatThrownBy(
            () -> authenticator.authenticate(anonymousGroupId, "AGMember " + UUID.randomUUID()))
        .isInstanceOf(UnauthorizedException.class);
  }
}
