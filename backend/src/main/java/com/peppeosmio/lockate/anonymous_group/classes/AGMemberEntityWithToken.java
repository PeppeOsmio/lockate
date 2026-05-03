package com.peppeosmio.lockate.anonymous_group.classes;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;

public record AGMemberEntityWithToken(AGMemberEntity agMemberEntity, byte[] token) {
}
