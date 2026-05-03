package com.peppeosmio.lockate.anonymous_group.security;

import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class AGMemberAuthenticator {

    private final AnonymousGroupService anonymousGroupService;

    public AGMemberAuthenticator(AnonymousGroupService anonymousGroupService) {
        this.anonymousGroupService = anonymousGroupService;
    }

    public Authentication authenticate(
            UUID anonymousGroupId,
            String authorizationHeader
    ) throws UnauthorizedException, AGNotFoundException {

        log.info("Running AGMemberAuthenticator");

        if (authorizationHeader == null || !authorizationHeader.startsWith("AGMember ")) {
            throw new UnauthorizedException();
        }

        var parts = authorizationHeader.substring("AGMember ".length()).split("\\s+");
        UUID memberId = UUID.fromString(parts[0]);
        String token = parts[1];

        return anonymousGroupService.authenticateMember(
                anonymousGroupId, memberId, token
        );
    }
}

