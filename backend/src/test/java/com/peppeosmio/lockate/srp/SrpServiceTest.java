package com.peppeosmio.lockate.srp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.anonymous_group.exceptions.SrpSessionNotFoundException;
import com.peppeosmio.lockate.redis.RedisService;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.bouncycastle.crypto.agreement.srp.SRP6Client;
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups;
import org.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SrpServiceTest {

  @Mock private RedisService redisService;

  private SrpService srpService;

  private final SecureRandom random = new SecureRandom();
  private final byte[] salt = new byte[16];
  private final byte[] identity = "user@test.com".getBytes();
  private final byte[] password = "correct-horse-battery-staple".getBytes();
  private final byte[] wrongPassword = "wrong-password".getBytes();

  private BigInteger verifier;

  @BeforeEach
  void setUp() {
    random.nextBytes(salt);
    srpService = new SrpService(redisService, new ObjectMapper().findAndRegisterModules());

    var gen = new SRP6VerifierGenerator();
    gen.init(SRP6StandardGroups.rfc5054_2048, new SHA256Digest());
    verifier = gen.generateVerifier(salt, identity, password);
  }

  @Test
  void startSrp_returnsValidSession() throws Exception {
    var client = newClient();
    var A = client.generateClientCredentials(salt, identity, password);

    var result = srpService.startSrp(A, verifier);

    assertThat(result.sessionId()).startsWith("srp:");
    assertThat(result.srpSession().A()).isNotNull();
    assertThat(result.srpSession().B()).isNotNull();
    assertThat(result.srpSession().b()).isNotNull();
    assertThat(result.srpSession().createdAt()).isNotNull();
  }

  @Test
  void startSrp_savesSessionInRedisWithFiveMinuteTtl() throws Exception {
    var client = newClient();
    var A = client.generateClientCredentials(salt, identity, password);
    var captor = ArgumentCaptor.forClass(String.class);

    var result = srpService.startSrp(A, verifier);

    verify(redisService)
        .saveValue(eq(result.sessionId()), captor.capture(), eq(Duration.ofMinutes(5)));
    assertThat(captor.getValue()).contains("\"A\"").contains("\"B\"");
  }

  @Test
  void verifySrp_returnsTrueForCorrectPassword() throws Exception {
    var client = newClient();
    var A = client.generateClientCredentials(salt, identity, password);

    var sessionJson = startAndCaptureSession(A);
    var result = sessionJson.result();
    when(redisService.getValue(result.sessionId())).thenReturn(Optional.of(sessionJson.json()));

    var B = new BigInteger(Base64.getDecoder().decode(result.srpSession().B()));
    client.calculateSecret(B);
    var M1 = client.calculateClientEvidenceMessage();

    assertThat(srpService.verifySrp(result.sessionId(), verifier, M1)).isTrue();
  }

  @Test
  void verifySrp_throwsForWrongPassword() throws Exception {
    var client = newClient();
    var A = client.generateClientCredentials(salt, identity, wrongPassword);

    var sessionJson = startAndCaptureSession(A);
    var result = sessionJson.result();
    when(redisService.getValue(result.sessionId())).thenReturn(Optional.of(sessionJson.json()));

    var B = new BigInteger(Base64.getDecoder().decode(result.srpSession().B()));
    client.calculateSecret(B);
    var M1 = client.calculateClientEvidenceMessage();

    assertThat(srpService.verifySrp(result.sessionId(), verifier, M1)).isFalse();
  }

  @Test
  void verifySrp_throwsWhenSessionNotFound() {
    when(redisService.getValue("srp:nonexistent")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> srpService.verifySrp("srp:nonexistent", verifier, BigInteger.ONE))
        .isInstanceOf(SrpSessionNotFoundException.class);
  }

  @Test
  void verifySrp_deletesSessionFromRedisAfterUse() throws Exception {
    var client = newClient();
    var A = client.generateClientCredentials(salt, identity, password);

    var sessionJson = startAndCaptureSession(A);
    var result = sessionJson.result();
    when(redisService.getValue(result.sessionId())).thenReturn(Optional.of(sessionJson.json()));

    var B = new BigInteger(Base64.getDecoder().decode(result.srpSession().B()));
    client.calculateSecret(B);
    var M1 = client.calculateClientEvidenceMessage();
    srpService.verifySrp(result.sessionId(), verifier, M1);

    verify(redisService).deleteValue(result.sessionId());
  }

  private SRP6Client newClient() {
    var client = new SRP6Client();
    client.init(SRP6StandardGroups.rfc5054_2048, new SHA256Digest(), random);
    return client;
  }

  private record CapturedSession(SrpSessionResult result, String json) {}

  private CapturedSession startAndCaptureSession(BigInteger A) throws Exception {
    var captor = ArgumentCaptor.forClass(String.class);
    var result = srpService.startSrp(A, verifier);
    verify(redisService).saveValue(eq(result.sessionId()), captor.capture(), any());
    return new CapturedSession(result, captor.getValue());
  }
}
