package com.peppeosmio.lockate.anonymous_group.dto;

import java.util.List;

public record AGMemberAuthVerifyResDto(AnonymousGroupDto anonymousGroup,
                                       AGMemberWithTokenDto authenticatedMemberInfo,
                                       List<AGMemberDto> members) {
}
