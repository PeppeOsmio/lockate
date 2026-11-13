package com.peppeosmio.lockate.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Sends FCM data messages through the HTTP v1 API, without the Firebase Admin SDK. */
@ApplicationScoped
public class FcmClient {
  private static final Logger LOG = Logger.getLogger(FcmClient.class);

  public enum Result {
    SENT,
    UNREGISTERED,
    FAILED
  }

  private final URI sendUri;
  private final AccessTokenProvider accessTokens;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public FcmClient(
      @ConfigProperty(name = "relay.fcm.endpoint") String endpoint,
      @ConfigProperty(name = "relay.fcm.project-id") String projectId,
      AccessTokenProvider accessTokens,
      ObjectMapper objectMapper) {
    this.sendUri = URI.create(endpoint + "/v1/projects/" + projectId + "/messages:send");
    this.accessTokens = accessTokens;
    this.objectMapper = objectMapper;
  }

  /** High priority: every push the backend sends ends in a notification the user sees. */
  public Result send(String token, Map<String, String> data) {
    try {
      var body =
          objectMapper.writeValueAsString(
              Map.of(
                  "message",
                  Map.of("token", token, "data", data, "android", Map.of("priority", "HIGH"))));
      var request =
          HttpRequest.newBuilder(sendUri)
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + accessTokens.accessToken())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return switch (response.statusCode()) {
        case 200 -> Result.SENT;
        // the token is gone for good (app uninstalled, token rotated)
        case 404 -> Result.UNREGISTERED;
        default -> {
          LOG.warnf("FCM answered %d", response.statusCode());
          yield Result.FAILED;
        }
      };
    } catch (IOException e) {
      LOG.warnf("Can't reach FCM: %s", e.getMessage());
      return Result.FAILED;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.FAILED;
    }
  }
}
