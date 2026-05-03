package com.peppeosmio.lockate.anonymous_group.service.result;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;

public record AGMemberAuthResult(AnonymousGroupEntity anonymousGroupEntity,
                                 AGMemberEntity agMemberEntity,
                                 AGMemberAuthentication authentication) {
}
