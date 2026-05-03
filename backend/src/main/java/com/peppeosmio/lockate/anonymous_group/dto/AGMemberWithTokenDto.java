package com.peppeosmio.lockate.anonymous_group.dto;

import com.peppeosmio.lockate.anonymous_group.classes.AGMemberEntityWithToken;

import java.util.Base64;

public record AGMemberWithTokenDto(AGMemberDto member, String token) {
}
