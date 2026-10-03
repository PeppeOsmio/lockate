package com.peppeosmio.lockate.anonymous_group.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthenticator;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AGSendLocationHandshakeInterceptorTest {

  @Mock private AGMemberAuthenticator agMemberAuthenticator;
  @Mock private ServerHttpRequest request;
  @Mock private ServerHttpResponse response;
  @Mock private WebSocketHandler wsHandler;

  private AGSendLocationHandshakeInterceptor interceptor;
  private Map<String, Object> attributes;

  @BeforeEach
  void setUp() {
    interceptor = new AGSendLocationHandshakeInterceptor(agMemberAuthenticator, new ObjectMapper());
    attributes = new HashMap<>();
  }

  private void withPath(String path, String authHeader) {
    when(request.getURI()).thenReturn(URI.create("http://localhost" + path));
    var headers = new HttpHeaders();
    if (authHeader != null) {
      headers.add("Authorization", authHeader);
    }
    org.mockito.Mockito.lenient().when(request.getHeaders()).thenReturn(headers);
  }

  @Test
  void beforeHandshake_wellFormedPathAndSuccessfulAuth_returnsTrue() throws Exception {
    var groupId = UUID.randomUUID();
    var memberId = UUID.randomUUID();
    var authentication =
        new com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication(memberId);
    withPath(
        "/api/ws/anonymous-groups/" + groupId + "/send-location",
        "AGMember " + memberId + " token");
    when(agMemberAuthenticator.authenticate(groupId, "AGMember " + memberId + " token"))
        .thenReturn(authentication);

    var result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

    assertThat(result).isTrue();
    assertThat(attributes.get("anonymousGroupId")).isEqualTo(groupId);
    assertThat(attributes.get("authentication")).isSameAs(authentication);
  }

  @Test
  void beforeHandshake_malformedGroupIdSegment_returns400() throws Exception {
    withPath("/api/ws/anonymous-groups/not-a-uuid/send-location", "AGMember token");

    var result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

    assertThat(result).isFalse();
    org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
  }

  @Test
  void beforeHandshake_tooShortPath_returns400() throws Exception {
    when(request.getURI()).thenReturn(URI.create("http://localhost/api/ws"));

    var result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

    assertThat(result).isFalse();
    org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
  }

  @Test
  void beforeHandshake_agNotFoundException_returns404() throws Exception {
    var groupId = UUID.randomUUID();
    withPath("/api/ws/anonymous-groups/" + groupId + "/send-location", "AGMember token");
    when(agMemberAuthenticator.authenticate(any(), any()))
        .thenThrow(new AGNotFoundException(groupId));

    var result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

    assertThat(result).isFalse();
    org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.NOT_FOUND);
  }

  @Test
  void beforeHandshake_unauthorizedException_returns401() throws Exception {
    var groupId = UUID.randomUUID();
    withPath("/api/ws/anonymous-groups/" + groupId + "/send-location", "AGMember token");
    when(agMemberAuthenticator.authenticate(any(), any())).thenThrow(new UnauthorizedException());

    var result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

    assertThat(result).isFalse();
    org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void beforeHandshake_unexpectedException_returns500() throws Exception {
    var groupId = UUID.randomUUID();
    withPath("/api/ws/anonymous-groups/" + groupId + "/send-location", "AGMember token");
    when(agMemberAuthenticator.authenticate(any(), any())).thenThrow(new RuntimeException("boom"));

    var result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

    assertThat(result).isFalse();
    org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
