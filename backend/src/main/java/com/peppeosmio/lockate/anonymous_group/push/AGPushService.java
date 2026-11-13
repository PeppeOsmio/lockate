package com.peppeosmio.lockate.anonymous_group.push;

import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where to push a member's notifications, and the pushes themselves: the same frames the group's
 * sockets get. One registration per member, replaced by the next one, so nobody can take over
 * another member's registration id.
 */
@Service
public class AGPushService {
  public static final int MAX_TOKEN_LENGTH = 4096;
  public static final int REGISTRATION_ID_BYTES = 16;
  public static final int KEY_BYTES = 32;

  private final AGMemberRepository agMemberRepository;
  private final AGPushSender agPushSender;

  public AGPushService(AGMemberRepository agMemberRepository, AGPushSender agPushSender) {
    this.agMemberRepository = agMemberRepository;
    this.agPushSender = agPushSender;
  }

  /** The registered members of the group, but {@code exceptMemberId} (who caused the push). */
  public List<PushTarget> targets(UUID anonymousGroupId, UUID exceptMemberId) {
    return agMemberRepository.findByAnonymousGroupIdAndPushTokenIsNotNull(anonymousGroupId).stream()
        .filter(member -> !member.getId().equals(exceptMemberId))
        .map(PushTarget::of)
        .toList();
  }

  public void notifyGroup(UUID anonymousGroupId, UUID exceptMemberId, AGFrameDto frame) {
    agPushSender.send(targets(anonymousGroupId, exceptMemberId), frame);
  }

  public void notifyMember(AGMemberEntity member, AGFrameDto frame) {
    if (member.getPushToken() != null) {
      agPushSender.send(List.of(PushTarget.of(member)), frame);
    }
  }

  /** For targets read before the members are deleted. */
  public void notifyTargets(List<PushTarget> targets, AGFrameDto frame) {
    agPushSender.send(targets, frame);
  }

  @Transactional
  public AGPushRegistrationResDto register(
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
    return new AGPushRegistrationResDto(agPushSender.isEnabled());
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
