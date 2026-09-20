package com.peppeosmio.lockate.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.peppeosmio.lockate.common.exceptions.NotFoundException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleAccessDenied_returns401() {
    var response = handler.handleAccessDenied(new AccessDeniedException("denied"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error()).isEqualTo("access_denied");
  }

  @Test
  void handleNotFound_returns404() {
    var response = handler.handleNotFound(new NotFoundException("id"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error()).isEqualTo("not_found");
  }

  @Test
  void handleUnauthorized_returns401() {
    var response = handler.handleUnauthorized(new UnauthorizedException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error()).isEqualTo("unauthorized");
  }

  @Test
  void handleException_sseAcceptHeader_returnsEmptyBody() {
    var request = new MockHttpServletRequest();
    request.addHeader("Accept", "text/event-stream");
    var webRequest = new ServletWebRequest(request);

    var response = handler.handleException(new RuntimeException("boom"), webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void handleException_jsonAcceptHeader_returnsJsonBody() {
    var request = new MockHttpServletRequest();
    request.addHeader("Accept", "application/json");
    var webRequest = new ServletWebRequest(request);

    var response = handler.handleException(new RuntimeException("boom"), webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().error()).isEqualTo("boom");
  }
}
