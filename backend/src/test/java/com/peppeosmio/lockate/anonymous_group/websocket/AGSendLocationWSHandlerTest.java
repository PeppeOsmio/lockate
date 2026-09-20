package com.peppeosmio.lockate.anonymous_group.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.anonymous_group.dto.AGLocationSaveReqDto;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.anonymous_group.service.AnonymousGroupService;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class AGSendLocationWSHandlerTest {

  @Mock private AnonymousGroupService anonymousGroupService;
  @Mock private WebSocketSession session;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AGSendLocationWSHandler handler;
  private Map<String, Object> sessionAttributes;
  private final UUID groupId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    handler = new AGSendLocationWSHandler(objectMapper, anonymousGroupService);
    sessionAttributes = new HashMap<>();
    sessionAttributes.put("anonymousGroupId", groupId);
    sessionAttributes.put("authentication", new AGMemberAuthentication(memberId));
    when(session.getAttributes()).thenReturn(sessionAttributes);
  }

  private TextMessage locationMessage() throws Exception {
    var dto =
        new AGLocationSaveReqDto(
            new EncryptedDataDto(
                java.util.Base64.getEncoder().encodeToString("coord".getBytes()),
                java.util.Base64.getEncoder().encodeToString("iv".getBytes())));
    return new TextMessage(objectMapper.writeValueAsString(dto));
  }

  @Test
  void afterConnectionEstablished_withValidAuthentication_doesNotThrow() {
    assertThatCode(() -> handler.afterConnectionEstablished(session)).doesNotThrowAnyException();
  }

  @Test
  void afterConnectionEstablished_missingAuthentication_throwsUnauthorized() {
    sessionAttributes.remove("authentication");

    assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void handleTextMessage_firstMessage_passesNullLastSavedTimestamp() throws Exception {
    when(anonymousGroupService.saveLocation(eq(groupId), any(), any(), any()))
        .thenReturn(Optional.of(LocalDateTime.now()));

    handler.handleTextMessage(session, locationMessage());

    verify(anonymousGroupService).saveLocation(eq(groupId), any(), any(), isNull());
  }

  @Test
  void handleTextMessage_rapidSecondMessage_passesPreviouslySavedTimestampFromCache()
      throws Exception {
    var firstTimestamp = LocalDateTime.now();
    when(anonymousGroupService.saveLocation(eq(groupId), any(), any(), isNull()))
        .thenReturn(Optional.of(firstTimestamp));
    handler.handleTextMessage(session, locationMessage());

    when(anonymousGroupService.saveLocation(eq(groupId), any(), any(), eq(firstTimestamp)))
        .thenReturn(Optional.empty());
    handler.handleTextMessage(session, locationMessage());

    var timestampCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(anonymousGroupService, times(2))
        .saveLocation(eq(groupId), any(), any(), timestampCaptor.capture());
    assertThat(timestampCaptor.getAllValues()).containsExactly(null, firstTimestamp);
  }

  @Test
  void handleTextMessage_agNotFoundException_closesWithBadData() throws Exception {
    when(anonymousGroupService.saveLocation(any(), any(), any(), any()))
        .thenThrow(new AGNotFoundException(groupId));

    handler.handleTextMessage(session, locationMessage());

    verify(session).close(CloseStatus.BAD_DATA);
  }

  @Test
  void handleTextMessage_unauthorizedException_closesWithBadData() throws Exception {
    when(anonymousGroupService.saveLocation(any(), any(), any(), any()))
        .thenThrow(new UnauthorizedException());

    handler.handleTextMessage(session, locationMessage());

    verify(session).close(CloseStatus.BAD_DATA);
  }

  @Test
  void handleTextMessage_missingAuthentication_closesWithBadData() throws Exception {
    sessionAttributes.remove("authentication");

    handler.handleTextMessage(session, locationMessage());

    verify(session).close(CloseStatus.BAD_DATA);
  }

  @Test
  void handleTextMessage_unexpectedException_closesWithServerError() throws Exception {
    when(anonymousGroupService.saveLocation(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("boom"));

    handler.handleTextMessage(session, locationMessage());

    verify(session).close(CloseStatus.SERVER_ERROR);
  }
}
