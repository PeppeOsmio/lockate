package com.peppeosmio.lockate.invite;

import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.invite.dto.InviteCreateReqDto;
import com.peppeosmio.lockate.invite.dto.InviteCreateResDto;
import com.peppeosmio.lockate.invite.dto.InviteeConfirmDto;
import com.peppeosmio.lockate.invite.dto.InviteeMessageDto;
import com.peppeosmio.lockate.invite.dto.InviteeProgressDto;
import com.peppeosmio.lockate.invite.dto.InviterConfirmDto;
import com.peppeosmio.lockate.invite.dto.InviterFinalDto;
import com.peppeosmio.lockate.invite.dto.InviterHelloDto;
import com.peppeosmio.lockate.invite.dto.InviterMessageDto;
import com.peppeosmio.lockate.invite.exceptions.InvalidClaimTokenException;
import com.peppeosmio.lockate.invite.exceptions.InvalidInviteMessageException;
import com.peppeosmio.lockate.invite.exceptions.InvalidJoinTokenException;
import com.peppeosmio.lockate.invite.exceptions.InviteAlreadyClaimedException;
import com.peppeosmio.lockate.invite.exceptions.InviteConflictException;
import com.peppeosmio.lockate.invite.exceptions.InviteNotFoundException;
import com.peppeosmio.lockate.invite.exceptions.TooManyRequestsException;
import com.peppeosmio.lockate.redis.RedisService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Relays the J-PAKE invite exchange between an existing member and an invitee through a short-lived
 * mailbox in Redis. The messages are opaque to the server: it can't check them, it only enforces
 * sizes, a single invitee per invite and rate limits. Joining needs the one-time join token, which
 * the inviter only ever sends inside the payload encrypted for the invitee.
 */
@Service
public class InviteService {

  static final Duration INVITE_TTL = Duration.ofMinutes(10);
  private static final int MAX_NAMEPLATE = 9999;
  private static final int NAMEPLATE_ATTEMPTS = 50;
  private static final int SID_BYTES = 16;
  // J-PAKE round 1 is two points and two proofs (about 330 bytes), round 2 one of each (about 165)
  private static final int MAX_ROUND_ONE_BYTES = 512;
  private static final int MAX_ROUND_TWO_BYTES = 256;
  private static final int TAG_BYTES = 32;
  private static final int PAYLOAD_IV_BYTES = 12;
  private static final int MAX_PAYLOAD_BYTES = 1024;
  private static final int TOKEN_BYTES = 32;
  private static final int MAX_INVITES_PER_MEMBER_PER_HOUR = 10;
  // Fetching an inviter's share and replying to it are what someone guessing codes would repeat
  private static final int MAX_GUESS_REQUESTS_PER_IP_PER_MINUTE = 20;
  // The invitee polls for the inviter's final message about once a second
  private static final int MAX_POLL_REQUESTS_PER_IP_PER_MINUTE = 120;

  private final RedisService redisService;
  private final ObjectMapper objectMapper;
  private final SecureRandom secureRandom = new SecureRandom();

  public InviteService(RedisService redisService, ObjectMapper objectMapper) {
    this.redisService = redisService;
    this.objectMapper = objectMapper;
  }

  private static String inviteKey(UUID inviteId) {
    return "invite:" + inviteId;
  }

  private static String nameplateKey(int nameplate) {
    return "invite-nameplate:" + nameplate;
  }

  private static String claimKey(UUID inviteId) {
    return "invite-claim:" + inviteId;
  }

  private static String joinKey(UUID anonymousGroupId, String joinTokenHash) {
    return "invite-join:" + anonymousGroupId + ":" + joinTokenHash;
  }

  private static LocalDateTime now() {
    return LocalDateTime.now(ZoneOffset.UTC);
  }

  public InviteCreateResDto createInvite(
      UUID anonymousGroupId, AGMemberAuthentication authentication, InviteCreateReqDto dto)
      throws TooManyRequestsException, InvalidInviteMessageException, Base64Exception {
    checkRateLimit(
        "rate:invite-create:" + authentication.getId(),
        MAX_INVITES_PER_MEMBER_PER_HOUR,
        Duration.ofHours(1));
    requireSize(decode(dto.sid()), SID_BYTES);
    requireAtMost(decode(dto.round1()), MAX_ROUND_ONE_BYTES);

    var inviteId = UUID.randomUUID();
    var expiresAt = now().plus(INVITE_TTL);
    var nameplate = claimNameplate(inviteId);
    var joinToken = randomToken();
    var joinTokenHash = sha256Hex(joinToken);

    save(
        new InviteRecord(
            inviteId,
            anonymousGroupId,
            nameplate,
            joinTokenHash,
            null,
            dto.sid(),
            dto.round1(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            expiresAt));
    redisService.saveValue(
        joinKey(anonymousGroupId, joinTokenHash), inviteId.toString(), INVITE_TTL);
    return new InviteCreateResDto(
        inviteId, nameplate, Base64.getEncoder().encodeToString(joinToken), expiresAt);
  }

  public InviteeProgressDto getInviteeProgress(UUID anonymousGroupId, UUID inviteId)
      throws InviteNotFoundException {
    var invite = getMemberInvite(anonymousGroupId, inviteId);
    return new InviteeProgressDto(invite.round1Invitee(), invite.round2Invitee(), invite.tb());
  }

  public void confirmInvite(UUID anonymousGroupId, UUID inviteId, InviterConfirmDto dto)
      throws InviteNotFoundException,
          InviteConflictException,
          InvalidInviteMessageException,
          Base64Exception {
    var invite = getMemberInvite(anonymousGroupId, inviteId);
    if (invite.round1Invitee() == null || invite.ta() != null) {
      throw new InviteConflictException();
    }
    requireAtMost(decode(dto.round2()), MAX_ROUND_TWO_BYTES);
    requireSize(decode(dto.ta()), TAG_BYTES);
    save(invite.withInviterConfirmation(dto.round2(), dto.ta()));
  }

  public void postFinal(UUID anonymousGroupId, UUID inviteId, InviterFinalDto dto)
      throws InviteNotFoundException,
          InviteConflictException,
          InvalidInviteMessageException,
          Base64Exception {
    var invite = getMemberInvite(anonymousGroupId, inviteId);
    if (invite.tb() == null || invite.payloadCipherText() != null) {
      throw new InviteConflictException();
    }
    if (dto.payload() == null) {
      throw new InvalidInviteMessageException();
    }
    requireSize(decode(dto.payload().iv()), PAYLOAD_IV_BYTES);
    var cipherText = decode(dto.payload().cipherText());
    if (cipherText.length == 0 || cipherText.length > MAX_PAYLOAD_BYTES) {
      throw new InvalidInviteMessageException();
    }
    save(invite.withPayload(dto.payload().cipherText(), dto.payload().iv()));
  }

  public void cancelInvite(UUID anonymousGroupId, UUID inviteId) throws InviteNotFoundException {
    delete(getMemberInvite(anonymousGroupId, inviteId));
  }

  public InviterHelloDto getInviterHello(int nameplate, String clientIp)
      throws TooManyRequestsException, InviteNotFoundException {
    checkGuessRateLimit(clientIp);
    var invite = getByNameplate(nameplate);
    return new InviterHelloDto(invite.sid(), invite.round1Inviter());
  }

  /**
   * @return the claim token, which the invitee needs to read the inviter's messages and to post its
   *     confirmation. Only its hash is kept, so someone who knows just the nameplate can't do
   *     either.
   */
  public String postInviteeMessage(int nameplate, String clientIp, InviteeMessageDto dto)
      throws TooManyRequestsException,
          InviteNotFoundException,
          InviteAlreadyClaimedException,
          InvalidInviteMessageException,
          Base64Exception {
    checkGuessRateLimit(clientIp);
    var invite = getByNameplate(nameplate);
    requireAtMost(decode(dto.round1()), MAX_ROUND_ONE_BYTES);
    requireAtMost(decode(dto.round2()), MAX_ROUND_TWO_BYTES);
    // Only one invitee per invite: anyone else who tries the code gets a conflict, and the inviter
    // only ever answers the first reply
    if (!redisService.setIfAbsent(claimKey(invite.id()), "claimed", remainingTtl(invite))) {
      throw new InviteAlreadyClaimedException();
    }
    var claimToken = randomToken();
    save(invite.withInviteeMessage(sha256Hex(claimToken), dto.round1(), dto.round2()));
    return Base64.getEncoder().encodeToString(claimToken);
  }

  public InviterMessageDto getInviterMessage(int nameplate, String clientIp, String claimToken)
      throws TooManyRequestsException, InviteNotFoundException, InvalidClaimTokenException {
    checkPollRateLimit(clientIp);
    var invite = getClaimedInvite(nameplate, claimToken);
    var payload =
        invite.payloadCipherText() == null
            ? null
            : new EncryptedDataDto(invite.payloadCipherText(), invite.payloadIv());
    return new InviterMessageDto(invite.round2Inviter(), invite.ta(), payload);
  }

  public void postInviteeConfirmation(
      int nameplate, String clientIp, String claimToken, InviteeConfirmDto dto)
      throws TooManyRequestsException,
          InviteNotFoundException,
          InvalidClaimTokenException,
          InviteConflictException,
          InvalidInviteMessageException,
          Base64Exception {
    checkPollRateLimit(clientIp);
    var invite = getClaimedInvite(nameplate, claimToken);
    if (invite.ta() == null || invite.tb() != null) {
      throw new InviteConflictException();
    }
    requireSize(decode(dto.tb()), TAG_BYTES);
    save(invite.withInviteeConfirmation(dto.tb()));
  }

  /**
   * Accepts a join token once, for the group it was created for. GETDEL makes the check atomic, so
   * concurrent joins with the same token can't both succeed.
   */
  public void consumeJoinToken(UUID anonymousGroupId, String joinToken)
      throws InvalidJoinTokenException {
    byte[] token;
    try {
      token = Base64.getDecoder().decode(joinToken);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidJoinTokenException();
    }
    var inviteId =
        redisService
            .getAndDelete(joinKey(anonymousGroupId, sha256Hex(token)))
            .orElseThrow(InvalidJoinTokenException::new);
    load(UUID.fromString(inviteId)).ifPresent(this::delete);
  }

  private int claimNameplate(UUID inviteId) throws TooManyRequestsException {
    for (int attempt = 0; attempt < NAMEPLATE_ATTEMPTS; attempt++) {
      var nameplate = 1 + secureRandom.nextInt(MAX_NAMEPLATE);
      if (redisService.setIfAbsent(nameplateKey(nameplate), inviteId.toString(), INVITE_TTL)) {
        return nameplate;
      }
    }
    throw new TooManyRequestsException();
  }

  private void checkGuessRateLimit(String clientIp) throws TooManyRequestsException {
    checkRateLimit(
        "rate:invite-guess:" + clientIp,
        MAX_GUESS_REQUESTS_PER_IP_PER_MINUTE,
        Duration.ofMinutes(1));
  }

  private void checkPollRateLimit(String clientIp) throws TooManyRequestsException {
    checkRateLimit(
        "rate:invite-poll:" + clientIp, MAX_POLL_REQUESTS_PER_IP_PER_MINUTE, Duration.ofMinutes(1));
  }

  private byte[] randomToken() {
    var token = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(token);
    return token;
  }

  private void checkRateLimit(String key, int max, Duration window)
      throws TooManyRequestsException {
    if (redisService.increment(key, window) > max) {
      throw new TooManyRequestsException();
    }
  }

  private InviteRecord getMemberInvite(UUID anonymousGroupId, UUID inviteId)
      throws InviteNotFoundException {
    return load(inviteId)
        .filter(invite -> invite.anonymousGroupId().equals(anonymousGroupId))
        .orElseThrow(InviteNotFoundException::new);
  }

  private InviteRecord getClaimedInvite(int nameplate, String claimToken)
      throws InviteNotFoundException, InvalidClaimTokenException {
    var invite = getByNameplate(nameplate);
    byte[] token;
    try {
      token = Base64.getDecoder().decode(claimToken);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidClaimTokenException();
    }
    if (invite.claimTokenHash() == null
        || !MessageDigest.isEqual(
            invite.claimTokenHash().getBytes(StandardCharsets.UTF_8),
            sha256Hex(token).getBytes(StandardCharsets.UTF_8))) {
      throw new InvalidClaimTokenException();
    }
    return invite;
  }

  private InviteRecord getByNameplate(int nameplate) throws InviteNotFoundException {
    return redisService
        .getValue(nameplateKey(nameplate))
        .flatMap(inviteId -> load(UUID.fromString(inviteId)))
        .orElseThrow(InviteNotFoundException::new);
  }

  private Optional<InviteRecord> load(UUID inviteId) {
    return redisService
        .getValue(inviteKey(inviteId))
        .map(json -> objectMapper.readValue(json, InviteRecord.class))
        .filter(invite -> invite.expiresAt().isAfter(now()));
  }

  private void save(InviteRecord invite) {
    redisService.saveValue(
        inviteKey(invite.id()), objectMapper.writeValueAsString(invite), remainingTtl(invite));
  }

  private void delete(InviteRecord invite) {
    redisService.deleteValue(inviteKey(invite.id()));
    redisService.deleteValue(nameplateKey(invite.nameplate()));
    redisService.deleteValue(claimKey(invite.id()));
    redisService.deleteValue(joinKey(invite.anonymousGroupId(), invite.joinTokenHash()));
  }

  private static Duration remainingTtl(InviteRecord invite) {
    return Duration.between(now(), invite.expiresAt());
  }

  private static byte[] decode(String base64)
      throws Base64Exception, InvalidInviteMessageException {
    if (base64 == null) {
      throw new InvalidInviteMessageException();
    }
    try {
      return Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException e) {
      throw new Base64Exception();
    }
  }

  private static void requireSize(byte[] bytes, int size) throws InvalidInviteMessageException {
    if (bytes.length != size) {
      throw new InvalidInviteMessageException();
    }
  }

  private static void requireAtMost(byte[] bytes, int max) throws InvalidInviteMessageException {
    if (bytes.length == 0 || bytes.length > max) {
      throw new InvalidInviteMessageException();
    }
  }

  private static String sha256Hex(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available on the JVM", e);
    }
  }
}
