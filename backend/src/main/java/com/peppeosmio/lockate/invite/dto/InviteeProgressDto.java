package com.peppeosmio.lockate.invite.dto;

/** What the invitee has posted so far, for the inviter's polling. Missing parts are null. */
public record InviteeProgressDto(String round1, String round2, String tb) {}
