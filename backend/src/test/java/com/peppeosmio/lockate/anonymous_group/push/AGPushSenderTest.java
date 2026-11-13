package com.peppeosmio.lockate.anonymous_group.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.peppeosmio.lockate.anonymous_group.dto.AGFrameDto;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AGPushSenderTest {
  @Mock private AGMemberRepository agMemberRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<String> relayBodies = new ArrayList<>();
  private volatile int relayStatus = 200;
  private HttpServer relay;
  private final UUID memberId = UUID.randomUUID();
  private final byte[] registrationId = new byte[16];
  private final byte[] key = new byte[32];

  @BeforeEach
  void setUp() throws Exception {
    registrationId[0] = 5;
    key[0] = 7;
    relay = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    relay.createContext(
        "/v1/push",
        exchange -> {
          relayBodies.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(relayStatus, -1);
          exchange.close();
        });
    relay.start();
  }

  @AfterEach
  void tearDown() {
    relay.stop(0);
  }

  private AGPushSender sender(String relayUrl) {
    return new AGPushSender(relayUrl, objectMapper, agMemberRepository, Runnable::run);
  }

  private String relayUrl() {
    return "http://127.0.0.1:" + relay.getAddress().getPort();
  }

  private PushTarget target() {
    return new PushTarget(memberId, "fcm-token", registrationId, key);
  }

  private byte[] open(String r, String iv, String c) throws Exception {
    var cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(key, "AES"),
        new GCMParameterSpec(128, Base64.getDecoder().decode(iv)));
    cipher.updateAAD(("lockate-push-v1|" + r).getBytes(StandardCharsets.UTF_8));
    return cipher.doFinal(Base64.getDecoder().decode(c));
  }

  @Test
  void seal_onlyTheRegistrationIdIsInClear_theFrameOpensWithTheKeyAndAad() throws Exception {
    var frame = AGFrameDto.memberLeft(memberId);
    var plaintext = objectMapper.writeValueAsBytes(frame);

    var data = sender(relayUrl()).seal(target(), plaintext);

    assertThat(data).containsOnlyKeys("r", "iv", "c");
    assertThat(data.get("r")).isEqualTo(Base64.getEncoder().encodeToString(registrationId));
    assertThat(Base64.getDecoder().decode(data.get("iv"))).hasSize(12);
    assertThat(open(data.get("r"), data.get("iv"), data.get("c"))).isEqualTo(plaintext);
  }

  @Test
  void seal_aPayloadMovedToAnotherRegistrationDoesntOpen() throws Exception {
    var data = sender(relayUrl()).seal(target(), "x".getBytes());

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                open(
                    Base64.getEncoder().encodeToString(new byte[16]),
                    data.get("iv"),
                    data.get("c")))
        .isInstanceOf(javax.crypto.AEADBadTagException.class);
  }

  @Test
  void send_postsTheTokenAndTheSealedFrameToTheRelay() throws Exception {
    var frame = AGFrameDto.groupDeleted();

    sender(relayUrl()).send(List.of(target()), frame);

    var body = objectMapper.readTree(relayBodies.getFirst());
    assertThat(body.get("token").asString()).isEqualTo("fcm-token");
    var data = body.get("data");
    var opened =
        objectMapper.readValue(
            open(data.get("r").asString(), data.get("iv").asString(), data.get("c").asString()),
            AGFrameDto.class);
    assertThat(opened.type()).isEqualTo(AGFrameDto.GROUP_DELETED);
    verifyNoInteractions(agMemberRepository);
  }

  @Test
  void send_aTokenTheRelayDoesntKnow_dropsTheRegistration() {
    relayStatus = 404;

    sender(relayUrl()).send(List.of(target()), AGFrameDto.groupDeleted());

    verify(agMemberRepository).clearPushRegistration(memberId, registrationId);
  }

  @Test
  void send_anotherFailure_keepsTheRegistration() {
    relayStatus = 502;

    sender(relayUrl()).send(List.of(target()), AGFrameDto.groupDeleted());

    verifyNoInteractions(agMemberRepository);
  }

  @Test
  void send_withoutARelay_sendsNothing() {
    sender("").send(List.of(target()), AGFrameDto.groupDeleted());

    assertThat(relayBodies).isEmpty();
  }
}
