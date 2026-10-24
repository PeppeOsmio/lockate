package com.peppeosmio.lockate.invite.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record InviteCreateResDto(
    UUID inviteId, int nameplate, String joinToken, LocalDateTime expiresAt) {}
