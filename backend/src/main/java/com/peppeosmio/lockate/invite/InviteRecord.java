package com.peppeosmio.lockate.invite;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/**
 * An invite mailbox as stored in Redis. Every message is an opaque base64 blob: the server relays
 * the J-PAKE exchange without being able to check or read it. The join token and the claim token
 * are kept only as hashes.
 */
@RegisterReflectionForBinding
public record InviteRecord(
    UUID id,
    UUID anonymousGroupId,
    int nameplate,
    String joinTokenHash,
    String claimTokenHash,
    String sid,
    String round1Inviter,
    String round1Invitee,
    String round2Invitee,
    String round2Inviter,
    String ta,
    String tb,
    String payloadCipherText,
    String payloadIv,
    LocalDateTime expiresAt) {

  InviteRecord withInviteeMessage(
      String claimTokenHash, String round1Invitee, String round2Invitee) {
    return new InviteRecord(
        id,
        anonymousGroupId,
        nameplate,
        joinTokenHash,
        claimTokenHash,
        sid,
        round1Inviter,
        round1Invitee,
        round2Invitee,
        round2Inviter,
        ta,
        tb,
        payloadCipherText,
        payloadIv,
        expiresAt);
  }

  InviteRecord withInviterConfirmation(String round2Inviter, String ta) {
    return new InviteRecord(
        id,
        anonymousGroupId,
        nameplate,
        joinTokenHash,
        claimTokenHash,
        sid,
        round1Inviter,
        round1Invitee,
        round2Invitee,
        round2Inviter,
        ta,
        tb,
        payloadCipherText,
        payloadIv,
        expiresAt);
  }

  InviteRecord withInviteeConfirmation(String tb) {
    return new InviteRecord(
        id,
        anonymousGroupId,
        nameplate,
        joinTokenHash,
        claimTokenHash,
        sid,
        round1Inviter,
        round1Invitee,
        round2Invitee,
        round2Inviter,
        ta,
        tb,
        payloadCipherText,
        payloadIv,
        expiresAt);
  }

  InviteRecord withPayload(String payloadCipherText, String payloadIv) {
    return new InviteRecord(
        id,
        anonymousGroupId,
        nameplate,
        joinTokenHash,
        claimTokenHash,
        sid,
        round1Inviter,
        round1Invitee,
        round2Invitee,
        round2Inviter,
        ta,
        tb,
        payloadCipherText,
        payloadIv,
        expiresAt);
  }
}
