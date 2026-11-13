package com.peppeosmio.lockate.anonymous_group.push;

import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where to push a member's notifications. One registration per member, replaced by the next one, so
 * nobody can take over another member's registration id.
 */
@Service
public class AGPushService {
  public static final int MAX_TOKEN_LENGTH = 4096;
  public static final int REGISTRATION_ID_BYTES = 16;
  public static final int KEY_BYTES = 32;

  private final AGMemberRepository agMemberRepository;

  public AGPushService(AGMemberRepository agMemberRepository) {
    this.agMemberRepository = agMemberRepository;
  }

  @Transactional
  public void register(
      UUID anonymousGroupId, AGMemberAuthentication authentication, AGPushRegistrationReqDto dto)
      throws InvalidPushRegistrationException, UnauthorizedException {
    var registrationId = decode(dto.registrationId(), REGISTRATION_ID_BYTES);
    var key = decode(dto.key(), KEY_BYTES);
    var member =
        agMemberRepository
            .findByIdAndAnonymousGroupId(authentication.getId(), anonymousGroupId)
            .orElseThrow(UnauthorizedException::new);
    member.registerPush(dto.fcmToken(), registrationId, key);
    agMemberRepository.save(member);
  }

  private static byte[] decode(String base64, int size) throws InvalidPushRegistrationException {
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException e) {
      throw new InvalidPushRegistrationException();
    }
    if (bytes.length != size) {
      throw new InvalidPushRegistrationException();
    }
    return bytes;
  }
}
