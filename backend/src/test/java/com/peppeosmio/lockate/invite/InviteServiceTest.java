package com.peppeosmio.lockate.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.invite.dto.InviteCreateReqDto;
import com.peppeosmio.lockate.invite.dto.InviteeConfirmDto;
import com.peppeosmio.lockate.invite.dto.InviteeMessageDto;
import com.peppeosmio.lockate.invite.dto.InviteeProgressDto;
import com.peppeosmio.lockate.invite.dto.InviterConfirmDto;
import com.peppeosmio.lockate.invite.dto.InviterFinalDto;
import com.peppeosmio.lockate.invite.dto.InviterMessageDto;
import com.peppeosmio.lockate.invite.exceptions.InvalidClaimTokenException;
import com.peppeosmio.lockate.invite.exceptions.InvalidInviteMessageException;
import com.peppeosmio.lockate.invite.exceptions.InvalidJoinTokenException;
import com.peppeosmio.lockate.invite.exceptions.InviteAlreadyClaimedException;
import com.peppeosmio.lockate.invite.exceptions.InviteConflictException;
import com.peppeosmio.lockate.invite.exceptions.InviteNotFoundException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

  @Mock private RedisService redisService;

  private final Map<String, String> store = new HashMap<>();
  private final Map<String, Duration> ttls = new HashMap<>();
  private final Map<String, Long> counters = new HashMap<>();
  private final Base64.Encoder encoder = Base64.getEncoder();
  private final UUID groupId = UUID.randomUUID();
  private final AGMemberAuthentication member = new AGMemberAuthentication(UUID.randomUUID());
  private final String ip = "203.0.113.7";

  private InviteService service;

  /** RedisService backed by a map, so tests check behaviour instead of individual calls */
  @BeforeEach
  void setUp() {
    lenient()
        .doAnswer(
            inv -> {
              store.put(inv.getArgument(0), inv.getArgument(1));
              ttls.put(inv.getArgument(0), inv.getArgument(2));
              return null;
            })
        .when(redisService)
        .saveValue(anyString(), anyString(), any());
    lenient()
        .when(redisService.getValue(anyString()))
        .thenAnswer(inv -> Optional.ofNullable(store.get((String) inv.getArgument(0))));
    lenient()
        .doAnswer(inv -> store.remove((String) inv.getArgument(0)))
        .when(redisService)
        .deleteValue(anyString());
    lenient()
        .when(redisService.setIfAbsent(anyString(), anyString(), any()))
        .thenAnswer(
            inv -> {
              ttls.put(inv.getArgument(0), inv.getArgument(2));
              return store.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null;
            });
    lenient()
        .when(redisService.getAndDelete(anyString()))
        .thenAnswer(inv -> Optional.ofNullable(store.remove((String) inv.getArgument(0))));
    lenient()
        .when(redisService.increment(anyString(), any()))
        .thenAnswer(inv -> counters.merge(inv.getArgument(0), 1L, Long::sum));
    service = new InviteService(redisService, JsonMapper.builder().build());
  }

  private String b64(int length) {
    var bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = (byte) (i + 1);
    return encoder.encodeToString(bytes);
  }

  private InviteCreateReqDto createReq() {
    return new InviteCreateReqDto(b64(16), b64(330));
  }

  private InviteeMessageDto inviteeMessage() {
    return new InviteeMessageDto(b64(330), b64(165));
  }

  private InviterConfirmDto inviterConfirm() {
    return new InviterConfirmDto(b64(165), b64(32));
  }

  private InviteeConfirmDto inviteeConfirm() {
    return new InviteeConfirmDto(b64(32));
  }

  private InviterFinalDto finalMessage() {
    return new InviterFinalDto(new EncryptedDataDto(b64(100), b64(12)));
  }

  /** Runs the exchange up to the invitee's claim and returns its claim token */
  private String claim(int nameplate) throws Exception {
    return service.postInviteeMessage(nameplate, ip, inviteeMessage());
  }

  // ---------- createInvite ----------

  @Test
  void createInvite_returnsNameplateTokenAndTenMinuteExpiry() throws Exception {
    var result = service.createInvite(groupId, member, createReq());

    assertThat(result.nameplate()).isBetween(1, 9999);
    assertThat(Base64.getDecoder().decode(result.joinToken())).hasSize(32);
    assertThat(result.expiresAt())
        .isBetween(
            LocalDateTime.now(ZoneOffset.UTC).plusMinutes(9),
            LocalDateTime.now(ZoneOffset.UTC).plusMinutes(11));
    assertThat(ttls.values()).allMatch(ttl -> ttl.compareTo(Duration.ofMinutes(10)) <= 0);
  }

  @Test
  void createInvite_neverStoresTheJoinTokenOrTheClaimTokenInClear() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    var claimToken = claim(created.nameplate());

    assertThat(store.toString()).doesNotContain(created.joinToken()).doesNotContain(claimToken);
  }

  @Test
  void createInvite_givesDifferentNameplatesToConcurrentInvites() throws Exception {
    var first = service.createInvite(groupId, member, createReq());
    var second =
        service.createInvite(groupId, new AGMemberAuthentication(UUID.randomUUID()), createReq());

    assertThat(first.nameplate()).isNotEqualTo(second.nameplate());
    assertThat(first.inviteId()).isNotEqualTo(second.inviteId());
  }

  @Test
  void createInvite_rejectsWrongSizes() {
    assertThatThrownBy(
            () -> service.createInvite(groupId, member, new InviteCreateReqDto(b64(15), b64(330))))
        .isInstanceOf(InvalidInviteMessageException.class);
    assertThatThrownBy(
            () -> service.createInvite(groupId, member, new InviteCreateReqDto(b64(16), b64(513))))
        .isInstanceOf(InvalidInviteMessageException.class);
    assertThatThrownBy(
            () -> service.createInvite(groupId, member, new InviteCreateReqDto(b64(16), b64(0))))
        .isInstanceOf(InvalidInviteMessageException.class);
  }

  @Test
  void createInvite_rejectsInvalidBase64() {
    assertThatThrownBy(
            () -> service.createInvite(groupId, member, new InviteCreateReqDto("!!", b64(330))))
        .isInstanceOf(Base64Exception.class);
  }

  @Test
  void createInvite_isLimitedPerMember() throws Exception {
    for (int i = 0; i < 10; i++) {
      service.createInvite(groupId, member, createReq());
    }

    assertThatThrownBy(() -> service.createInvite(groupId, member, createReq()))
        .isInstanceOf(TooManyRequestsException.class);
  }

  // ---------- full exchange ----------

  @Test
  void fullExchange_relaysTheMessagesAndTheTokenLetsTheInviteeJoinOnce() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    var nameplate = created.nameplate();

    var hello = service.getInviterHello(nameplate, ip);
    assertThat(hello.sid()).isEqualTo(b64(16));
    assertThat(hello.round1()).isEqualTo(b64(330));

    assertThat(service.getInviteeProgress(groupId, created.inviteId()))
        .isEqualTo(new InviteeProgressDto(null, null, null));
    var claimToken = claim(nameplate);
    assertThat(service.getInviteeProgress(groupId, created.inviteId()))
        .isEqualTo(new InviteeProgressDto(b64(330), b64(165), null));

    assertThat(service.getInviterMessage(nameplate, ip, claimToken))
        .isEqualTo(new InviterMessageDto(null, null, null));
    service.confirmInvite(groupId, created.inviteId(), inviterConfirm());
    assertThat(service.getInviterMessage(nameplate, ip, claimToken))
        .isEqualTo(new InviterMessageDto(b64(165), b64(32), null));

    service.postInviteeConfirmation(nameplate, ip, claimToken, inviteeConfirm());
    assertThat(service.getInviteeProgress(groupId, created.inviteId()).tb()).isEqualTo(b64(32));

    service.postFinal(groupId, created.inviteId(), finalMessage());
    assertThat(service.getInviterMessage(nameplate, ip, claimToken).payload())
        .isEqualTo(finalMessage().payload());

    service.consumeJoinToken(groupId, created.joinToken());

    assertThatThrownBy(() -> service.consumeJoinToken(groupId, created.joinToken()))
        .isInstanceOf(InvalidJoinTokenException.class);
    assertThatThrownBy(() -> service.getInviterHello(nameplate, ip))
        .isInstanceOf(InviteNotFoundException.class);
  }

  @Test
  void postInviteeMessage_onlyTheFirstInviteeIsAccepted() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    claim(created.nameplate());

    assertThatThrownBy(
            () -> service.postInviteeMessage(created.nameplate(), "198.51.100.1", inviteeMessage()))
        .isInstanceOf(InviteAlreadyClaimedException.class);
    assertThat(service.getInviteeProgress(groupId, created.inviteId()).round1())
        .isEqualTo(b64(330));
  }

  @Test
  void postInviteeMessage_rejectsWrongSizes() throws Exception {
    var created = service.createInvite(groupId, member, createReq());

    assertThatThrownBy(
            () ->
                service.postInviteeMessage(
                    created.nameplate(), ip, new InviteeMessageDto(b64(330), b64(257))))
        .isInstanceOf(InvalidInviteMessageException.class);
    assertThatThrownBy(
            () ->
                service.postInviteeMessage(
                    created.nameplate(), ip, new InviteeMessageDto(b64(513), b64(165))))
        .isInstanceOf(InvalidInviteMessageException.class);
  }

  @Test
  void postInviteeMessage_aRejectedMessageDoesNotUseUpTheInvite() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    assertThatThrownBy(
            () ->
                service.postInviteeMessage(
                    created.nameplate(), ip, new InviteeMessageDto(b64(330), b64(257))))
        .isInstanceOf(InvalidInviteMessageException.class);

    claim(created.nameplate());
  }

  @Test
  void inviterMessagesNeedTheClaimToken() throws Exception {
    var created = service.createInvite(groupId, member, createReq());

    assertThatThrownBy(() -> service.getInviterMessage(created.nameplate(), ip, "anything"))
        .isInstanceOf(InvalidClaimTokenException.class);

    var claimToken = claim(created.nameplate());
    var other = encoder.encodeToString(new byte[32]);
    assertThat(other).isNotEqualTo(claimToken);
    assertThatThrownBy(() -> service.getInviterMessage(created.nameplate(), ip, other))
        .isInstanceOf(InvalidClaimTokenException.class);
    assertThatThrownBy(
            () -> service.postInviteeConfirmation(created.nameplate(), ip, other, inviteeConfirm()))
        .isInstanceOf(InvalidClaimTokenException.class);
  }

  @Test
  void confirmInvite_beforeTheInviteeRepliedIsAConflict() throws Exception {
    var created = service.createInvite(groupId, member, createReq());

    assertThatThrownBy(() -> service.confirmInvite(groupId, created.inviteId(), inviterConfirm()))
        .isInstanceOf(InviteConflictException.class);
  }

  @Test
  void confirmInvite_twiceIsAConflict() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    claim(created.nameplate());
    service.confirmInvite(groupId, created.inviteId(), inviterConfirm());

    assertThatThrownBy(() -> service.confirmInvite(groupId, created.inviteId(), inviterConfirm()))
        .isInstanceOf(InviteConflictException.class);
  }

  @Test
  void confirmInvite_rejectsWrongSizes() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    claim(created.nameplate());

    assertThatThrownBy(
            () ->
                service.confirmInvite(
                    groupId, created.inviteId(), new InviterConfirmDto(b64(257), b64(32))))
        .isInstanceOf(InvalidInviteMessageException.class);
    assertThatThrownBy(
            () ->
                service.confirmInvite(
                    groupId, created.inviteId(), new InviterConfirmDto(b64(165), b64(31))))
        .isInstanceOf(InvalidInviteMessageException.class);
  }

  @Test
  void postInviteeConfirmation_beforeTheInviterConfirmedIsAConflict() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    var claimToken = claim(created.nameplate());

    assertThatThrownBy(
            () ->
                service.postInviteeConfirmation(
                    created.nameplate(), ip, claimToken, inviteeConfirm()))
        .isInstanceOf(InviteConflictException.class);
  }

  @Test
  void postInviteeConfirmation_twiceIsAConflictAndWrongSizesAreRejected() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    var claimToken = claim(created.nameplate());
    service.confirmInvite(groupId, created.inviteId(), inviterConfirm());

    assertThatThrownBy(
            () ->
                service.postInviteeConfirmation(
                    created.nameplate(), ip, claimToken, new InviteeConfirmDto(b64(31))))
        .isInstanceOf(InvalidInviteMessageException.class);
    service.postInviteeConfirmation(created.nameplate(), ip, claimToken, inviteeConfirm());
    assertThatThrownBy(
            () ->
                service.postInviteeConfirmation(
                    created.nameplate(), ip, claimToken, inviteeConfirm()))
        .isInstanceOf(InviteConflictException.class);
  }

  @Test
  void postFinal_beforeTheInviteeConfirmedIsAConflict() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    claim(created.nameplate());
    service.confirmInvite(groupId, created.inviteId(), inviterConfirm());

    assertThatThrownBy(() -> service.postFinal(groupId, created.inviteId(), finalMessage()))
        .isInstanceOf(InviteConflictException.class);
  }

  /** Runs the exchange up to the invitee's confirmation */
  private ConfirmedInvite confirmed() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    var claimToken = claim(created.nameplate());
    service.confirmInvite(groupId, created.inviteId(), inviterConfirm());
    service.postInviteeConfirmation(created.nameplate(), ip, claimToken, inviteeConfirm());
    return new ConfirmedInvite(created.inviteId(), created.nameplate());
  }

  private record ConfirmedInvite(UUID inviteId, int nameplate) {}

  @Test
  void postFinal_twiceIsAConflict() throws Exception {
    var invite = confirmed();
    service.postFinal(groupId, invite.inviteId(), finalMessage());

    assertThatThrownBy(() -> service.postFinal(groupId, invite.inviteId(), finalMessage()))
        .isInstanceOf(InviteConflictException.class);
  }

  @Test
  void postFinal_rejectsAnOversizedOrMalformedPayload() throws Exception {
    var invite = confirmed();

    assertThatThrownBy(
            () ->
                service.postFinal(
                    groupId,
                    invite.inviteId(),
                    new InviterFinalDto(new EncryptedDataDto(b64(2000), b64(12)))))
        .isInstanceOf(InvalidInviteMessageException.class);
    assertThatThrownBy(
            () ->
                service.postFinal(
                    groupId,
                    invite.inviteId(),
                    new InviterFinalDto(new EncryptedDataDto(b64(100), b64(11)))))
        .isInstanceOf(InvalidInviteMessageException.class);
  }

  @Test
  void memberSideCallsOnAnotherGroupsInviteAreNotFound() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    var otherGroup = UUID.randomUUID();

    assertThatThrownBy(() -> service.getInviteeProgress(otherGroup, created.inviteId()))
        .isInstanceOf(InviteNotFoundException.class);
    assertThatThrownBy(() -> service.cancelInvite(otherGroup, created.inviteId()))
        .isInstanceOf(InviteNotFoundException.class);
    assertThatThrownBy(
            () -> service.confirmInvite(otherGroup, created.inviteId(), inviterConfirm()))
        .isInstanceOf(InviteNotFoundException.class);
  }

  @Test
  void unknownNameplateIsNotFound() {
    assertThatThrownBy(() -> service.getInviterHello(1234, ip))
        .isInstanceOf(InviteNotFoundException.class);
  }

  // ---------- consumeJoinToken ----------

  @Test
  void consumeJoinToken_isBoundToTheGroup() throws Exception {
    var created = service.createInvite(groupId, member, createReq());

    assertThatThrownBy(() -> service.consumeJoinToken(UUID.randomUUID(), created.joinToken()))
        .isInstanceOf(InvalidJoinTokenException.class);
    service.consumeJoinToken(groupId, created.joinToken());
  }

  @Test
  void consumeJoinToken_rejectsAnUnknownToken() {
    assertThatThrownBy(() -> service.consumeJoinToken(groupId, b64(32)))
        .isInstanceOf(InvalidJoinTokenException.class);
  }

  @Test
  void consumeJoinToken_rejectsInvalidBase64() {
    assertThatThrownBy(() -> service.consumeJoinToken(groupId, "!!"))
        .isInstanceOf(InvalidJoinTokenException.class);
  }

  // ---------- cancelInvite ----------

  @Test
  void cancelInvite_removesTheInviteAndInvalidatesTheToken() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    var claimToken = claim(created.nameplate());

    service.cancelInvite(groupId, created.inviteId());

    assertThatThrownBy(() -> service.getInviterHello(created.nameplate(), ip))
        .isInstanceOf(InviteNotFoundException.class);
    assertThatThrownBy(() -> service.getInviterMessage(created.nameplate(), ip, claimToken))
        .isInstanceOf(InviteNotFoundException.class);
    assertThatThrownBy(() -> service.consumeJoinToken(groupId, created.joinToken()))
        .isInstanceOf(InvalidJoinTokenException.class);
  }

  // ---------- rate limits ----------

  @Test
  void publicHelloAndInviteeMessageAreLimitedPerIp() throws Exception {
    for (int i = 0; i < 20; i++) {
      assertThatThrownBy(() -> service.getInviterHello(5000, ip))
          .isInstanceOf(InviteNotFoundException.class);
    }

    assertThatThrownBy(() -> service.getInviterHello(5000, ip))
        .isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  void pollingIsLimitedPerIpButMuchMoreGenerously() throws Exception {
    var created = service.createInvite(groupId, member, createReq());
    var claimToken = claim(created.nameplate());

    for (int i = 0; i < 120; i++) {
      service.getInviterMessage(created.nameplate(), ip, claimToken);
    }

    assertThatThrownBy(() -> service.getInviterMessage(created.nameplate(), ip, claimToken))
        .isInstanceOf(TooManyRequestsException.class);
  }
}
