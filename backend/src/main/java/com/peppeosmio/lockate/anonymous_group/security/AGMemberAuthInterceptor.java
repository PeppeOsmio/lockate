package com.peppeosmio.lockate.anonymous_group.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.anonymous_group.controllers.SecuredAGMember;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class AGMemberAuthInterceptor implements HandlerInterceptor {

    private final AGMemberAuthenticator agMemberAuthenticator;
    private final ObjectMapper objectMapper;

    public AGMemberAuthInterceptor(
            AGMemberAuthenticator agMemberAuthenticator, ObjectMapper objectMapper) {
        this.agMemberAuthenticator = agMemberAuthenticator;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        boolean secured =
                method.hasMethodAnnotation(SecuredAGMember.class)
                        || method.getBeanType().isAnnotationPresent(SecuredAGMember.class);

        if (!secured) {
            return true;
        }

        @SuppressWarnings("unchecked")
        var pathVariables =
                (Map<String, String>)
                        request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVariables == null) {
            return false;
        }

        if (!pathVariables.containsKey("anonymousGroupId")) {
            return false;
        }

        UUID anonymousGroupId = null;
        String authHeader = null;

        try {
            anonymousGroupId = UUID.fromString(pathVariables.get("anonymousGroupId"));
            authHeader = request.getHeader("Authorization");
        } catch (Exception ignored) {
        }

        if (anonymousGroupId == null || authHeader == null) {
            response.sendError(400);
            return false;
        }

        try {
            var authentication =
                    agMemberAuthenticator.authenticate(anonymousGroupId, authHeader);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (AGNotFoundException e) {
            e.printStackTrace();
            response.sendError(
                    404, objectMapper.writeValueAsString(new ErrorResponseDto("ag_not_found")));
            return false;
        } catch (UnauthorizedException e) {
            e.printStackTrace();
            response.sendError(
                    401, objectMapper.writeValueAsString(new ErrorResponseDto("unauthorized")));
            return false;
        }
    }
}
