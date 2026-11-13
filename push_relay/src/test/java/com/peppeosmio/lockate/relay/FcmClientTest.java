package com.peppeosmio.lockate.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FcmClientTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private StubServer fcm;
  private FcmClient client;

  @BeforeEach
  void setUp() throws Exception {
    fcm = new StubServer();
    client = new FcmClient(fcm.url(), "my-project", () -> "access-token", objectMapper);
  }

  @AfterEach
  void tearDown() {
    fcm.close();
  }

  @Test
  void sendsAHighPriorityDataMessageWithTheAccessToken() throws Exception {
    var result = client.send("phone-token", Map.of("r", "abc", "c", "sealed"));

    assertEquals(FcmClient.Result.SENT, result);
    var request = fcm.requests.getFirst();
    assertEquals("POST", request.method());
    assertEquals("/v1/projects/my-project/messages:send", request.path());
    assertEquals(List.of("Bearer access-token"), request.headers().get("Authorization"));
    var message = objectMapper.readTree(request.body()).get("message");
    assertEquals("phone-token", message.get("token").asText());
    assertEquals("sealed", message.get("data").get("c").asText());
    assertEquals("abc", message.get("data").get("r").asText());
    assertEquals("HIGH", message.get("android").get("priority").asText());
  }

  @Test
  void aTokenFcmDoesntKnowIsUnregistered() {
    fcm.status = 404;

    assertEquals(FcmClient.Result.UNREGISTERED, client.send("t", Map.of("c", "x")));
  }

  @Test
  void anyOtherErrorIsAFailure() {
    fcm.status = 400;
    assertEquals(FcmClient.Result.FAILED, client.send("t", Map.of("c", "x")));

    fcm.status = 503;
    assertEquals(FcmClient.Result.FAILED, client.send("t", Map.of("c", "x")));
  }

  @Test
  void anUnreachableFcmIsAFailure() {
    fcm.close();

    assertEquals(FcmClient.Result.FAILED, client.send("t", Map.of("c", "x")));
  }
}
