package com.peppeosmio.lockate.api_key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.api_key.dto.ApiKeyWriteDto;
import com.peppeosmio.lockate.common.dto.PageQueryDto;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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
            ApiKeyService.sha256Hex(secret),
            null,
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
            null);
    entity.setId(id);
    when(apiKeyRepository.findById(id)).thenReturn(Optional.of(entity));

    var result = service.verifyApiKey(id + ":" + secret);

    assertThat(result).isTrue();
    assertThat(entity.getLastValidated()).isNotNull();
    assertThat(entity.getLastValidated()).isAfter(entity.getCreatedAt());
  }

  @Test
  void verifyApiKey_usedMinutesAgo_doesNotWriteTheLastUseAgain() {
    var id = UUID.randomUUID();
    var entity =
        new ApiKeyEntity(
            ApiKeyService.sha256Hex("s"),
            null,
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
            null);
    entity.setId(id);
    var lastUse = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(3);
    entity.setLastValidated(lastUse);
    when(apiKeyRepository.findById(id)).thenReturn(Optional.of(entity));

    assertThat(service.verifyApiKey(id + ":s")).isTrue();
    assertThat(entity.getLastValidated()).isEqualTo(lastUse);
  }

  @Test
  void verifyApiKey_usedLongerAgo_writesTheLastUse() {
    var id = UUID.randomUUID();
    var entity =
        new ApiKeyEntity(
            ApiKeyService.sha256Hex("s"),
            null,
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
            null);
    entity.setId(id);
    var lastUse = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(11);
    entity.setLastValidated(lastUse);
    when(apiKeyRepository.findById(id)).thenReturn(Optional.of(entity));

    assertThat(service.verifyApiKey(id + ":s")).isTrue();
    assertThat(entity.getLastValidated()).isAfter(lastUse);
  }

  @Test
  void verifyApiKey_expiredKey_returnsFalse() {
    var id = UUID.randomUUID();
    var now = LocalDateTime.now(ZoneOffset.UTC);
    var entity =
        new ApiKeyEntity(ApiKeyService.sha256Hex("s"), null, now.minusDays(2), now.minusMinutes(1));
    entity.setId(id);
    when(apiKeyRepository.findById(id)).thenReturn(Optional.of(entity));

    assertThat(service.verifyApiKey(id + ":s")).isFalse();
    assertThat(entity.getLastValidated()).isNull();
  }

  @Test
  void verifyApiKey_notYetExpiredKey_returnsTrue() {
    var id = UUID.randomUUID();
    var now = LocalDateTime.now(ZoneOffset.UTC);
    var entity =
        new ApiKeyEntity(ApiKeyService.sha256Hex("s"), null, now.minusDays(2), now.plusDays(1));
    entity.setId(id);
    when(apiKeyRepository.findById(id)).thenReturn(Optional.of(entity));

    assertThat(service.verifyApiKey(id + ":s")).isTrue();
  }

  @Test
  void verifyApiKey_wrongSecret_returnsFalse() {
    var id = UUID.randomUUID();
    var entity =
        new ApiKeyEntity(
            ApiKeyService.sha256Hex("correctsecret"),
            null,
            LocalDateTime.now(ZoneOffset.UTC),
            null);
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
    var expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
    when(apiKeyRepository.save(any()))
        .thenAnswer(
            inv -> {
              ApiKeyEntity saved = inv.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });

    var result = service.createApiKey(new ApiKeyWriteDto("  Family phones ", expiresAt));

    assertThat(result.key()).startsWith(result.id() + ":");
    assertThat(result.label()).isEqualTo("Family phones");
    assertThat(result.expiresAt()).isEqualTo(expiresAt);
    var saved = ArgumentCaptor.forClass(ApiKeyEntity.class);
    verify(apiKeyRepository).save(saved.capture());
    assertThat(saved.getValue().getSecretHash())
        .isEqualTo(ApiKeyService.sha256Hex(result.key().split(":", 2)[1]));
  }

  @Test
  void createApiKey_blankLabel_keepsNone() {
    when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.createApiKey(new ApiKeyWriteDto(" ", null));

    assertThat(result.label()).isNull();
    assertThat(result.expiresAt()).isNull();
  }

  @Test
  void updateApiKey_replacesLabelAndExpiry() {
    var id = UUID.randomUUID();
    var entity =
        new ApiKeyEntity("hash", "Old", LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now());
    entity.setId(id);
    when(apiKeyRepository.findById(id)).thenReturn(Optional.of(entity));

    var result = service.updateApiKey(id, new ApiKeyWriteDto("New", null));

    assertThat(result).isPresent();
    assertThat(result.get().label()).isEqualTo("New");
    assertThat(result.get().expiresAt()).isNull();
    assertThat(entity.getExpiresAt()).isNull();
  }

  @Test
  void updateApiKey_missingKey_returnsEmpty() {
    var id = UUID.randomUUID();
    when(apiKeyRepository.findById(id)).thenReturn(Optional.empty());

    assertThat(service.updateApiKey(id, new ApiKeyWriteDto("x", null))).isEmpty();
  }

  @Test
  void listApiKeys_returnsDtosWithLastValidated() {
    var entity = new ApiKeyEntity("hash", null, LocalDateTime.now(ZoneOffset.UTC), null);
    entity.setId(UUID.randomUUID());
    entity.setLastValidated(LocalDateTime.now(ZoneOffset.UTC));
    var pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
    when(apiKeyRepository.findAll(pageable))
        .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));

    var result = service.listApiKeys(new PageQueryDto(0, 20));

    assertThat(result.items()).hasSize(1);
    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.items().getFirst().id()).isEqualTo(entity.getId());
    assertThat(result.items().getFirst().lastValidated()).isEqualTo(entity.getLastValidated());
  }

  @Test
  void deleteApiKey_existingKey_deletesAndReturnsDto() {
    var id = UUID.randomUUID();
    var entity = new ApiKeyEntity("hash", null, LocalDateTime.now(ZoneOffset.UTC), null);
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
