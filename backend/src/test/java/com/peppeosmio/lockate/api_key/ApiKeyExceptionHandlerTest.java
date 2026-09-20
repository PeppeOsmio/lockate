package com.peppeosmio.lockate.api_key;

import static org.assertj.core.api.Assertions.assertThat;

import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiKeyExceptionHandlerTest {

  private final ApiKeyExceptionHandler handler = new ApiKeyExceptionHandler();

  @Test
  void handleBase64_returns400() {
    var response = handler.handleBase64(new Base64Exception());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error()).isEqualTo("invalid_base64");
  }
}
