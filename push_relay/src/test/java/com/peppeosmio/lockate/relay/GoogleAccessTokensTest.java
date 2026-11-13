package com.peppeosmio.lockate.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoogleAccessTokensTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private StubServer google;
  private long now = 1_000_000;

  @TempDir Path dir;

  @BeforeEach
  void setUp() throws Exception {
    google = new StubServer();
    google.body = "{\"access_token\":\"tok-1\",\"expires_in\":3600}";
  }

  @AfterEach
  void tearDown() {
    google.close();
  }

  private GoogleAccessTokens fromMetadata() {
    return new GoogleAccessTokens(Optional.empty(), google.url(), objectMapper, () -> now);
  }

  @Test
  void onGoogleCloudTheTokenComesFromTheMetadataServer() throws Exception {
    assertEquals("tok-1", fromMetadata().accessToken());

    var request = google.requests.getFirst();
    assertEquals(List.of("Google"), request.headers().get("Metadata-flavor"));
    assertTrue(
        request.path().startsWith("/computeMetadata/v1/instance/service-accounts/default/token"));
    assertTrue(
        URLDecoder.decode(request.path(), StandardCharsets.UTF_8)
            .endsWith("scopes=" + GoogleAccessTokens.SCOPE));
  }

  @Test
  void theTokenIsReusedUntilAMinuteBeforeItExpires() throws Exception {
    var tokens = fromMetadata();
    tokens.accessToken();

    now += 3_540_000 - 1;
    tokens.accessToken();
    assertEquals(1, google.requests.size());

    now += 1;
    tokens.accessToken();
    assertEquals(2, google.requests.size());
  }

  @Test
  void withAKeyFileItSignsAJwtForTheTokenEndpoint() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var keys = generator.generateKeyPair();
    var pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(keys.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    var file = dir.resolve("key.json");
    Files.writeString(
        file,
        objectMapper.writeValueAsString(
            Map.of(
                "client_email",
                "relay@my-project.iam.gserviceaccount.com",
                "private_key",
                pem,
                "token_uri",
                google.url() + "/token")));
    var tokens =
        new GoogleAccessTokens(
            Optional.of(file.toString()), "http://unused", objectMapper, () -> now);

    assertEquals("tok-1", tokens.accessToken());

    var request = google.requests.getFirst();
    assertEquals("/token", request.path());
    var form = request.body();
    assertTrue(form.startsWith("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"));
    var jwt = form.substring(form.indexOf("assertion=") + "assertion=".length()).split("\\.");
    var claims = objectMapper.readTree(Base64.getUrlDecoder().decode(jwt[1]));
    assertEquals("relay@my-project.iam.gserviceaccount.com", claims.get("iss").asText());
    assertEquals(GoogleAccessTokens.SCOPE, claims.get("scope").asText());
    assertEquals(google.url() + "/token", claims.get("aud").asText());
    assertEquals(now / 1000 + 3600, claims.get("exp").asLong());
    var verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(keys.getPublic());
    verifier.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
    assertTrue(verifier.verify(Base64.getUrlDecoder().decode(jwt[2])));
  }

  @Test
  void aRefusedTokenRequestThrows() {
    google.status = 403;

    assertThrows(IOException.class, () -> fromMetadata().accessToken());
  }
}
