package com.peppeosmio.lockate.anonymous_group.push;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import java.util.UUID;

/** A member's registration, read before the member may be deleted. */
public record PushTarget(UUID memberId, String token, byte[] registrationId, byte[] key) {
  static PushTarget of(AGMemberEntity member) {
    return new PushTarget(
        member.getId(), member.getPushToken(), member.getPushRegistrationId(), member.getPushKey());
  }
}
