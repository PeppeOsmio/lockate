package com.peppeosmio.lockate.anonymous_group.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AGPushServiceTest {

  @Mock private AGMemberRepository agMemberRepository;

  private AGPushService service;
  private final UUID groupId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final AGMemberAuthentication authentication = new AGMemberAuthentication(memberId);
  private AGMemberEntity member;

  @BeforeEach
  void setUp() {
    service = new AGPushService(agMemberRepository);
    var group =
        new AnonymousGroupEntity(groupId, new EncryptedString("n".getBytes(), "iv".getBytes()));
    member =
        new AGMemberEntity(
            memberId,
            new EncryptedString("m".getBytes(), "iv".getBytes()),
            "token".getBytes(),
            false,
            group);
  }

  private static String bytes(int size, int fill) {
    var b = new byte[size];
    java.util.Arrays.fill(b, (byte) fill);
    return Base64.getEncoder().encodeToString(b);
  }

  private static AGPushRegistrationReqDto registration(int idBytes, int keyBytes) {
    return new AGPushRegistrationReqDto("fcm-token", bytes(idBytes, 1), bytes(keyBytes, 2));
  }

  @Test
  void register_storesTheRegistrationWithTheMember() throws Exception {
    when(agMemberRepository.findByIdAndAnonymousGroupId(memberId, groupId))
        .thenReturn(Optional.of(member));

    service.register(groupId, authentication, registration(16, 32));

    assertThat(member.getPushToken()).isEqualTo("fcm-token");
    assertThat(member.getPushRegistrationId()).isEqualTo(Base64.getDecoder().decode(bytes(16, 1)));
    assertThat(member.getPushKey()).isEqualTo(Base64.getDecoder().decode(bytes(32, 2)));
    verify(agMemberRepository).save(member);
  }

  @Test
  void register_replacesThePreviousRegistration() throws Exception {
    when(agMemberRepository.findByIdAndAnonymousGroupId(memberId, groupId))
        .thenReturn(Optional.of(member));
    service.register(groupId, authentication, registration(16, 32));

    service.register(
        groupId,
        authentication,
        new AGPushRegistrationReqDto("new-token", bytes(16, 3), bytes(32, 4)));

    assertThat(member.getPushToken()).isEqualTo("new-token");
    assertThat(member.getPushRegistrationId()).isEqualTo(Base64.getDecoder().decode(bytes(16, 3)));
  }

  @Test
  void register_registrationIdOfTheWrongSize_throwsAndStoresNothing() {
    assertThatThrownBy(() -> service.register(groupId, authentication, registration(15, 32)))
        .isInstanceOf(InvalidPushRegistrationException.class);
    verify(agMemberRepository, never()).save(any());
  }

  @Test
  void register_keyOfTheWrongSize_throwsAndStoresNothing() {
    assertThatThrownBy(() -> service.register(groupId, authentication, registration(16, 31)))
        .isInstanceOf(InvalidPushRegistrationException.class);
    verify(agMemberRepository, never()).save(any());
  }

  @Test
  void register_notBase64_throws() {
    var dto = new AGPushRegistrationReqDto("fcm-token", "not base64!", bytes(32, 2));

    assertThatThrownBy(() -> service.register(groupId, authentication, dto))
        .isInstanceOf(InvalidPushRegistrationException.class);
  }

  @Test
  void register_memberNotInTheGroup_throwsUnauthorized() {
    when(agMemberRepository.findByIdAndAnonymousGroupId(memberId, groupId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.register(groupId, authentication, registration(16, 32)))
        .isInstanceOf(UnauthorizedException.class);
  }
}
