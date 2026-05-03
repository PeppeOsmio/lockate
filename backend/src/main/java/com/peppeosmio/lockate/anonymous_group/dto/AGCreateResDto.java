package com.peppeosmio.lockate.anonymous_group.dto;

public record AGCreateResDto(AnonymousGroupDto anonymousGroup,
                             AGMemberWithTokenDto authenticatedMemberInfo) {
}
