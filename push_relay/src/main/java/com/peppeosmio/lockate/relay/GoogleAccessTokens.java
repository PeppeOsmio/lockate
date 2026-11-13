package com.peppeosmio.lockate.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Gets the access token the way Google's libraries do, without them: from the metadata server when
 * running on Google Cloud (Cloud Run gives the service its own identity, so there is no key to
 * guard), or by signing a JWT with a service-account key file elsewhere. Cached until a minute
 * before it expires.
 */
@ApplicationScoped
public class GoogleAccessTokens implements AccessTokenProvider {
  static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
  private static final long EXPIRY_MARGIN_MILLIS = 60_000;

  private final Optional<String> credentialsFile;
  private final String metadataUrl;
  private final ObjectMapper objectMapper;
  private final LongSupplier now;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private String token;
  private long expiresAtMillis;

  @Inject
  public GoogleAccessTokens(
      @ConfigProperty(name = "relay.fcm.credentials-file") Optional<String> credentialsFile,
      @ConfigProperty(name = "relay.fcm.metadata-url") String metadataUrl,
      ObjectMapper objectMapper) {
    this(credentialsFile, metadataUrl, objectMapper, System::currentTimeMillis);
  }

  GoogleAccessTokens(
      Optional<String> credentialsFile,
      String metadataUrl,
      ObjectMapper objectMapper,
      LongSupplier now) {
    this.credentialsFile = credentialsFile.filter(file -> !file.isBlank());
    this.metadataUrl = metadataUrl;
    this.objectMapper = objectMapper;
    this.now = now;
  }

  @Override
  public synchronized String accessToken() throws IOException, InterruptedException {
    if (token != null && now.getAsLong() < expiresAtMillis - EXPIRY_MARGIN_MILLIS) {
      return token;
    }
    var response =
        credentialsFile.isPresent() ? fromKeyFile(credentialsFile.get()) : fromMetadata();
    token = response.get("access_token").asText();
    expiresAtMillis = now.getAsLong() + response.get("expires_in").asLong() * 1000;
    return token;
  }

  private JsonNode fromMetadata() throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    metadataUrl
                        + "/computeMetadata/v1/instance/service-accounts/default/token?scopes="
                        + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8)))
            .header("Metadata-Flavor", "Google")
            .GET()
            .build();
    return send(request);
  }

  private JsonNode fromKeyFile(String file) throws IOException, InterruptedException {
    var key = objectMapper.readTree(Files.readString(Path.of(file)));
    var tokenUri = key.get("token_uri").asText();
    var nowSeconds = now.getAsLong() / 1000;
    Map<String, Object> claims =
        Map.of(
            "iss", key.get("client_email").asText(),
            "scope", SCOPE,
            "aud", tokenUri,
            "iat", nowSeconds,
            "exp", nowSeconds + 3600);
    var assertion = signedJwt(claims, privateKey(key.get("private_key").asText()));
    var request =
        HttpRequest.newBuilder(URI.create(tokenUri))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "grant_type="
                        + URLEncoder.encode(
                            "urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                        + "&assertion="
                        + assertion))
            .build();
    return send(request);
  }

  private JsonNode send(HttpRequest request) throws IOException, InterruptedException {
    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("Token request answered " + response.statusCode());
    }
    return objectMapper.readTree(response.body());
  }

  private String signedJwt(Map<String, Object> claims, PrivateKey key) throws IOException {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    var header = encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
    var payload = encoder.encodeToString(objectMapper.writeValueAsBytes(claims));
    var signingInput = header + "." + payload;
    try {
      var signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(key);
      signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      return signingInput + "." + encoder.encodeToString(signature.sign());
    } catch (GeneralSecurityException e) {
      throw new IOException("Can't sign the token request", e);
    }
  }

  private static PrivateKey privateKey(String pem) throws IOException {
    var base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    try {
      return KeyFactory.getInstance("RSA")
          .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IOException("Invalid service-account key", e);
    }
  }
}
