package com.peppeosmio.lockate.relay;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PushResourceTest {
  @InjectMock FcmClient fcmClient;

  // tokens differ per test, so the per-token limit doesn't carry over
  private static String token() {
    return UUID.randomUUID().toString();
  }

  private static Map<String, Object> push(String token, Map<String, String> data) {
    var body = new HashMap<String, Object>();
    body.put("token", token);
    body.put("data", data);
    return body;
  }

  @Test
  void aPushFcmTakesIs200() {
    var token = token();
    when(fcmClient.send(anyString(), any())).thenReturn(FcmClient.Result.SENT);

    given()
        .contentType("application/json")
        .body(push(token, Map.of("r", "id", "c", "sealed")))
        .post("/v1/push")
        .then()
        .statusCode(200);

    verify(fcmClient).send(token, Map.of("r", "id", "c", "sealed"));
  }

  @Test
  void anUnregisteredTokenIs404() {
    when(fcmClient.send(anyString(), any())).thenReturn(FcmClient.Result.UNREGISTERED);

    given()
        .contentType("application/json")
        .body(push(token(), Map.of("c", "x")))
        .post("/v1/push")
        .then()
        .statusCode(404);
  }

  @Test
  void anFcmFailureIs502() {
    when(fcmClient.send(anyString(), any())).thenReturn(FcmClient.Result.FAILED);

    given()
        .contentType("application/json")
        .body(push(token(), Map.of("c", "x")))
        .post("/v1/push")
        .then()
        .statusCode(502);
  }

  @Test
  void aPushWithoutTokenOrDataIs400() {
    given()
        .contentType("application/json")
        .body(push("", Map.of("c", "x")))
        .post("/v1/push")
        .then()
        .statusCode(400);
    given()
        .contentType("application/json")
        .body(push(token(), Map.of()))
        .post("/v1/push")
        .then()
        .statusCode(400);

    verify(fcmClient, never()).send(anyString(), any());
  }

  @Test
  void tooMuchDataIs400() {
    given()
        .contentType("application/json")
        .body(push(token(), Map.of("c", "x".repeat(PushResource.MAX_DATA_CHARS))))
        .post("/v1/push")
        .then()
        .statusCode(400);

    verify(fcmClient, never()).send(anyString(), any());
  }

  @Test
  void overThePerTokenLimitIs429() {
    var token = token();
    when(fcmClient.send(anyString(), any())).thenReturn(FcmClient.Result.SENT);
    for (int i = 0; i < 30; i++) {
      given()
          .contentType("application/json")
          .body(push(token, Map.of("c", "x")))
          .post("/v1/push")
          .then()
          .statusCode(200);
    }

    given()
        .contentType("application/json")
        .body(push(token, Map.of("c", "x")))
        .post("/v1/push")
        .then()
        .statusCode(429);
  }
}
