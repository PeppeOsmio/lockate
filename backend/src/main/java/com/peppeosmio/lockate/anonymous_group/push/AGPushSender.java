package com.peppeosmio.lockate.anonymous_group.push;

import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Seals a frame for each member with the key its app gave us and posts it to the push relay, which
 * forwards it to FCM. Only the registration id is in clear, so neither the relay nor Google can
 * read or forge a push. Sent after the transaction commits, off the caller's thread.
 */
@Slf4j
@Service
public class AGPushSender {
  static final String AAD_PREFIX = "lockate-push-v1|";
  private static final int IV_BYTES = 12;

  private final String relayUrl;
  private final ObjectMapper objectMapper;
  private final AGMemberRepository agMemberRepository;
  private final Executor executor;
  private final SecureRandom secureRandom = new SecureRandom();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Autowired
  public AGPushSender(
      @Value("${lockate.push.relay-url}") String relayUrl,
      ObjectMapper objectMapper,
      AGMemberRepository agMemberRepository) {
    this(relayUrl, objectMapper, agMemberRepository, Executors.newVirtualThreadPerTaskExecutor());
  }

  AGPushSender(
      String relayUrl,
      ObjectMapper objectMapper,
      AGMemberRepository agMemberRepository,
      Executor executor) {
    this.relayUrl = relayUrl;
    this.objectMapper = objectMapper;
    this.agMemberRepository = agMemberRepository;
    this.executor = executor;
  }

  public boolean isEnabled() {
    return !relayUrl.isBlank();
  }

  /** Without a relay the server still works, with no notifications. */
  public void send(List<PushTarget> targets, AGFrameDto frame) {
    if (!isEnabled() || targets.isEmpty()) {
      return;
    }
    var plaintext = objectMapper.writeValueAsBytes(frame);
    Runnable dispatch =
        () -> targets.forEach(target -> executor.execute(() -> sendOne(target, plaintext)));
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              dispatch.run();
            }
          });
    } else {
      dispatch.run();
    }
  }

  private void sendOne(PushTarget target, byte[] plaintext) {
    try {
      var body =
          objectMapper.writeValueAsString(
              Map.of("token", target.token(), "data", seal(target, plaintext)));
      var request =
          HttpRequest.newBuilder(URI.create(relayUrl + "/v1/push"))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      var status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      if (status == 404) {
        // FCM no longer knows the token: the app registers again with a new one
        agMemberRepository.clearPushRegistration(target.memberId(), target.registrationId());
      } else if (status != 200) {
        log.warn("Push relay answered " + status);
      }
    } catch (IOException | GeneralSecurityException e) {
      log.warn("Can't push: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** {@code {r, iv, c}}: the registration id, and the frame sealed with the member's key. */
  Map<String, String> seal(PushTarget target, byte[] plaintext) throws GeneralSecurityException {
    var encoder = Base64.getEncoder();
    var registrationId = encoder.encodeToString(target.registrationId());
    var iv = new byte[IV_BYTES];
    secureRandom.nextBytes(iv);
    var cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        javax.crypto.Cipher.ENCRYPT_MODE,
        new javax.crypto.spec.SecretKeySpec(target.key(), "AES"),
        new javax.crypto.spec.GCMParameterSpec(128, iv));
    cipher.updateAAD((AAD_PREFIX + registrationId).getBytes(StandardCharsets.UTF_8));
    return Map.of(
        "r", registrationId,
        "iv", encoder.encodeToString(iv),
        "c", encoder.encodeToString(cipher.doFinal(plaintext)));
  }
}
