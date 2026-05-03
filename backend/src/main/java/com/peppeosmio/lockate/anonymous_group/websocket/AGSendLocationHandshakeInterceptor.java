package com.peppeosmio.lockate.anonymous_group.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthenticator;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class AGSendLocationHandshakeInterceptor implements HandshakeInterceptor {
    private final AGMemberAuthenticator agMemberAuthenticator;
    private final ObjectMapper objectMapper;

    public AGSendLocationHandshakeInterceptor(
            AGMemberAuthenticator agMemberAuthenticator, ObjectMapper objectMapper) {
        this.agMemberAuthenticator = agMemberAuthenticator;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws JsonProcessingException {

        var path = request.getURI().getPath();
        String[] segments = path.split("/");
        UUID anonymousGroupId;

        try {
            anonymousGroupId = UUID.fromString(segments[4]);
            attributes.put("anonymousGroupId", anonymousGroupId);
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        try {
            Authentication auth =
                    agMemberAuthenticator.authenticate(
                            anonymousGroupId, request.getHeaders().getFirst("Authorization"));
            attributes.put("authentication", auth);
            return true;
        } catch (AGNotFoundException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        catch (UnauthorizedException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        catch (Exception e) {
            e.printStackTrace();
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}
}
