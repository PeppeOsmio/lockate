package com.peppeosmio.lockate.api_key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

  @Mock private ApiKeyRepository apiKeyRepository;

  private ApiKeyService service;

  @BeforeEach
  void setUp() {
    service = new ApiKeyService(apiKeyRepository);
  }

  @Test
  void verifyApiKey_validHeader_returnsTrueAndUpdatesLastValidated() {
    var id = UUID.randomUUID();
    var secret = "mysecret";
    var entity =
        new ApiKeyEntity(
            ApiKeyService.sha256Hex(secret), LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
    entity.setId(id);
    when(apiKeyRepository.findById(id)).thenReturn(Optional.of(entity));

    var result = service.verifyApiKey(id + ":" + secret);

    assertThat(result).isTrue();
    assertThat(entity.getLastValidated()).isNotNull();
    assertThat(entity.getLastValidated()).isAfter(entity.getCreatedAt());
  }

  @Test
  void verifyApiKey_wrongSecret_returnsFalse() {
    var id = UUID.randomUUID();
    var entity =
        new ApiKeyEntity(
            ApiKeyService.sha256Hex("correctsecret"), LocalDateTime.now(ZoneOffset.UTC));
    entity.setId(id);
    when(apiKeyRepository.findById(id)).thenReturn(Optional.of(entity));

    var result = service.verifyApiKey(id + ":wrongsecret");

    assertThat(result).isFalse();
  }

  @Test
  void verifyApiKey_unknownId_returnsFalse() {
    var id = UUID.randomUUID();
    when(apiKeyRepository.findById(id)).thenReturn(Optional.empty());

    var result = service.verifyApiKey(id + ":anysecret");

    assertThat(result).isFalse();
  }

  @Test
  void verifyApiKey_malformedHeader_returnsFalse() {
    var result = service.verifyApiKey("not-a-valid-header");

    assertThat(result).isFalse();
    verify(apiKeyRepository, never()).findById(any());
  }

  @Test
  void verifyApiKey_malformedUuid_returnsFalse() {
    var result = service.verifyApiKey("not-a-uuid:somesecret");

    assertThat(result).isFalse();
    verify(apiKeyRepository, never()).findById(any());
  }

  @Test
  void createApiKey_savesAndReturnsKeyWithIdColonSecret() {
    var entity = new ApiKeyEntity("somehash", LocalDateTime.now(ZoneOffset.UTC));
    entity.setId(UUID.randomUUID());
    when(apiKeyRepository.save(any())).thenReturn(entity);

    var result = service.createApiKey();

    assertThat(result.id()).isEqualTo(entity.getId());
    assertThat(result.createdAt()).isEqualTo(entity.getCreatedAt());
    assertThat(result.key()).startsWith(entity.getId().toString() + ":");
  }

  @Test
  void listApiKeys_returnsDtosWithLastValidated() {
    var entity = new ApiKeyEntity("hash", LocalDateTime.now(ZoneOffset.UTC));
    entity.setId(UUID.randomUUID());
    entity.setLastValidated(LocalDateTime.now(ZoneOffset.UTC));
    when(apiKeyRepository.findAll()).thenReturn(List.of(entity));

    var result = service.listApiKeys();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().id()).isEqualTo(entity.getId());
    assertThat(result.getFirst().lastValidated()).isEqualTo(entity.getLastValidated());
  }

  @Test
  void deleteApiKey_existingKey_deletesAndReturnsDto() {
    var id = UUID.randomUUID();
    var entity = new ApiKeyEntity("hash", LocalDateTime.now(ZoneOffset.UTC));
    entity.setId(id);
    when(apiKeyRepository.findById(id)).thenReturn(Optional.of(entity));

    var result = service.deleteApiKey(id);

    assertThat(result).isPresent();
    verify(apiKeyRepository).deleteById(id);
  }

  @Test
  void deleteApiKey_missingKey_returnsEmptyAndDoesNotDelete() {
    var id = UUID.randomUUID();
    when(apiKeyRepository.findById(id)).thenReturn(Optional.empty());

    var result = service.deleteApiKey(id);

    assertThat(result).isEmpty();
    verify(apiKeyRepository, never()).deleteById(any());
  }
}
