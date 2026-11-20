package com.peppeosmio.lockate.api_key;

import com.peppeosmio.lockate.api_key.dto.ApiKeyCreatedDto;
import com.peppeosmio.lockate.api_key.dto.ApiKeyDto;
import com.peppeosmio.lockate.api_key.dto.ApiKeyWriteDto;
import com.peppeosmio.lockate.common.dto.PageQueryDto;
import com.peppeosmio.lockate.common.dto.PageResponseDto;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ApiKeyService {
  // Writing the last use on every request would make each one a database write
  private static final Duration LAST_USE_PRECISION = Duration.ofMinutes(10);

  private final ApiKeyRepository apiKeyRepository;

  public ApiKeyService(ApiKeyRepository apiKeyRepository) {
    this.apiKeyRepository = apiKeyRepository;
  }

  public ApiKeyCreatedDto createApiKey(ApiKeyWriteDto request) {
    var secretBytes = new byte[32];
    new SecureRandom().nextBytes(secretBytes);
    var secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    var entity =
        apiKeyRepository.save(
            new ApiKeyEntity(
                sha256Hex(secret),
                label(request.label()),
                LocalDateTime.now(ZoneOffset.UTC),
                request.expiresAt()));
    return new ApiKeyCreatedDto(
        entity.getId(),
        entity.getId() + ":" + secret,
        entity.getLabel(),
        entity.getCreatedAt(),
        entity.getExpiresAt());
  }

  @Transactional
  public Optional<ApiKeyDto> updateApiKey(UUID id, ApiKeyWriteDto request) {
    return apiKeyRepository
        .findById(id)
        .map(
            entity -> {
              entity.setLabel(label(request.label()));
              entity.setExpiresAt(request.expiresAt());
              return dto(entity);
            });
  }

  @Transactional
  public boolean verifyApiKey(String header) {
    var parts = header.split(":", 2);
    if (parts.length != 2) return false;
    UUID id;
    try {
      id = UUID.fromString(parts[0]);
    } catch (IllegalArgumentException e) {
      return false;
    }
    var entity = apiKeyRepository.findById(id).orElse(null);
    if (entity == null) return false;
    var hash = sha256Hex(parts[1]).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(hash, entity.getSecretHash().getBytes(StandardCharsets.UTF_8))) {
      return false;
    }
    var now = LocalDateTime.now(ZoneOffset.UTC);
    if (entity.getExpiresAt() != null && !now.isBefore(entity.getExpiresAt())) return false;
    if (entity.getLastValidated() == null
        || entity.getLastValidated().plus(LAST_USE_PRECISION).isBefore(now)) {
      entity.setLastValidated(now);
    }
    return true;
  }

  public PageResponseDto<ApiKeyDto> listApiKeys(PageQueryDto query) {
    var pageable = PageRequest.of(query.page(), query.size(), Sort.by("createdAt").descending());
    var page = apiKeyRepository.findAll(pageable);
    var items = page.getContent().stream().map(ApiKeyService::dto).toList();
    return PageResponseDto.of(page, items);
  }

  public Optional<ApiKeyDto> deleteApiKey(UUID id) {
    var entity = apiKeyRepository.findById(id).orElse(null);
    if (entity == null) return Optional.empty();
    apiKeyRepository.deleteById(id);
    return Optional.of(dto(entity));
  }

  private static ApiKeyDto dto(ApiKeyEntity entity) {
    return new ApiKeyDto(
        entity.getId(),
        entity.getLabel(),
        entity.getCreatedAt(),
        entity.getExpiresAt(),
        entity.getLastValidated());
  }

  private static String label(String label) {
    return label == null || label.isBlank() ? null : label.strip();
  }

  static String sha256Hex(String input) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      var hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
      var sb = new StringBuilder(64);
      for (byte b : hashBytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
