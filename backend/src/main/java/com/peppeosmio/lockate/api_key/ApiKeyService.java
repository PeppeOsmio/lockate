package com.peppeosmio.lockate.api_key;

import com.peppeosmio.lockate.api_key.dto.ApiKeyCreatedDto;
import com.peppeosmio.lockate.api_key.dto.ApiKeyDto;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.Base64;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ApiKeyService {
  private final ApiKeyRepository apiKeyRepository;

  public ApiKeyService(ApiKeyRepository apiKeyRepository) {
    this.apiKeyRepository = apiKeyRepository;
  }

  public ApiKeyCreatedDto createApiKey() {
    var secretBytes = new byte[32];
    new SecureRandom().nextBytes(secretBytes);
    var secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    var entity =
        apiKeyRepository.save(
            new ApiKeyEntity(sha256Hex(secret), LocalDateTime.now(ZoneOffset.UTC)));
    return new ApiKeyCreatedDto(
        entity.getId(), entity.getId() + ":" + secret, entity.getCreatedAt());
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
    if (!sha256Hex(parts[1]).equals(entity.getSecretHash())) return false;
    entity.setLastValidated(LocalDateTime.now(ZoneOffset.UTC));
    return true;
  }

  public List<ApiKeyDto> listApiKeys() {
    var entities = apiKeyRepository.findAll().iterator();
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(entities, Spliterator.ORDERED), false)
        .map(
            entity ->
                new ApiKeyDto(entity.getId(), entity.getCreatedAt(), entity.getLastValidated()))
        .toList();
  }

  public Optional<ApiKeyDto> deleteApiKey(UUID id) {
    var entity = apiKeyRepository.findById(id).orElse(null);
    if (entity == null) return Optional.empty();
    apiKeyRepository.deleteById(id);
    return Optional.of(
        new ApiKeyDto(entity.getId(), entity.getCreatedAt(), entity.getLastValidated()));
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
