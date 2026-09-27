package com.peppeosmio.lockate.anonymous_group;

import static org.assertj.core.api.Assertions.assertThat;

import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotAdminException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.srp.InvalidSrpSessionException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AGExceptionHandlerTest {

  private final AGExceptionHandler handler = new AGExceptionHandler();

  @Test
  void handleBase64_returns400() {
    var response = handler.handleBase64(new Base64Exception());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error()).isEqualTo("invalid_base64");
  }

  @Test
  void handleInvalidSrpSession_returns500() {
    var response = handler.handleInvalidSrpSession(new InvalidSrpSessionException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().error()).isEqualTo("invalid_srp_session");
  }

  @Test
  void handleAGNotFound_returns404() {
    var response = handler.handleAGNotFound(new AGNotFoundException(UUID.randomUUID()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error()).isEqualTo("ag_not_found");
  }

  @Test
  void handleAGMemberNotFound_returns404() {
    var response = handler.handleAGMemberNotFound(new AGMemberNotFoundException(UUID.randomUUID()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error()).isEqualTo("ag_member_not_found");
  }

  @Test
  void handleAGMemberNotAdmin_returns403() {
    var response = handler.handleAGMemberNotAdmin(new AGMemberNotAdminException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error()).isEqualTo("ag_member_not_admin");
  }
}
