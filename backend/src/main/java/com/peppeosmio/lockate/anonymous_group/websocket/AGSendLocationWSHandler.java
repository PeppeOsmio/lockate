package com.peppeosmio.lockate.anonymous_group.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.anonymous_group.dto.AGLocationSaveReqDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.utils.TTLMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class AGSendLocationWSHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AnonymousGroupService anonymousGroupService;
    private final TTLMap<UUID, LocalDateTime> lastSavedLocationsCache =
            new TTLMap<>(Duration.ofMinutes(5));

    public AGSendLocationWSHandler(
            ObjectMapper objectMapper, AnonymousGroupService anonymousGroupService) {
        this.objectMapper = objectMapper;
        this.anonymousGroupService = anonymousGroupService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var sessionAttributes = session.getAttributes();
        var anonymousGroupId = (UUID) sessionAttributes.get("anonymousGroupId");
        if(!(sessionAttributes.get("authentication") instanceof AGMemberAuthentication agMemberAuthentication)) {
            throw new UnauthorizedException();
        }
        log.info(
                "[WS] Receiving AG location: anonymousGroupId="
                        + anonymousGroupId
                        + " memberId="
                        + agMemberAuthentication.getId());
        super.afterConnectionEstablished(session);
    }

    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
            throws Exception {
        var sessionAttributes = session.getAttributes();
        var anonymousGroupId = (UUID) sessionAttributes.get("anonymousGroupId");
        var authentication = (AGMemberAuthentication) sessionAttributes.get("authentication");
        log.info(
                "[WS] Stopped receiving AG location: anonymousGroupId="
                        + anonymousGroupId
                        + " memberId="
                        + authentication.getId()
                        + " status="
                        + status);
        super.afterConnectionEstablished(session);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message)
            throws IOException {
        try {
            var sessionAttributes = session.getAttributes();
            var anonymousGroupId = (UUID) sessionAttributes.get("anonymousGroupId");
            if(!(sessionAttributes.get("authentication") instanceof AGMemberAuthentication agMemberAuthentication)) {
                throw new UnauthorizedException();
            }
            var dto = objectMapper.readValue(message.getPayload(), AGLocationSaveReqDto.class);
            var lastSavedLocationTimestamp =
                    lastSavedLocationsCache.get(agMemberAuthentication.getId()).orElse(null);
            var timestampSaved =
                    anonymousGroupService
                            .saveLocation(
                                    anonymousGroupId,
                                    agMemberAuthentication,
                                    dto,
                                    lastSavedLocationTimestamp)
                            .orElse(null);
            if (timestampSaved != null) {
                lastSavedLocationsCache.put(agMemberAuthentication.getId(), timestampSaved);
            }
        } catch (AGNotFoundException | UnauthorizedException | JsonProcessingException e) {
            session.close(CloseStatus.BAD_DATA);
        } catch (Exception e) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
}
