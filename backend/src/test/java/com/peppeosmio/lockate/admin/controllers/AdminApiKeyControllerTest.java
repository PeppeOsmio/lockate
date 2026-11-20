package com.peppeosmio.lockate.admin.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.api_key.ApiKeyService;
import com.peppeosmio.lockate.api_key.dto.ApiKeyCreatedDto;
import com.peppeosmio.lockate.api_key.dto.ApiKeyDto;
import com.peppeosmio.lockate.api_key.dto.ApiKeyWriteDto;
import com.peppeosmio.lockate.common.dto.PageQueryDto;
import com.peppeosmio.lockate.common.dto.PageResponseDto;
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
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdminApiKeyControllerTest {

  @Mock private ApiKeyService apiKeyService;

  private AdminApiKeyController controller;

  @BeforeEach
  void setUp() {
    controller = new AdminApiKeyController(apiKeyService);
  }

  @Test
  void listApiKeys_delegatesToServiceAndReturnsPage() {
    var dto = new ApiKeyDto(UUID.randomUUID(), null, LocalDateTime.now(ZoneOffset.UTC), null, null);
    var page = new PageResponseDto<>(List.of(dto), 0, 20, 1L, 1);
    var query = new PageQueryDto(0, 20);
    when(apiKeyService.listApiKeys(query)).thenReturn(page);

    var result = controller.listApiKeys(query);

    verify(apiKeyService).listApiKeys(query);
    assertThat(result.items()).hasSize(1);
  }

  @Test
  void deleteApiKey_existingKey_returns204() {
    var id = UUID.randomUUID();
    var dto = new ApiKeyDto(id, null, LocalDateTime.now(ZoneOffset.UTC), null, null);
    when(apiKeyService.deleteApiKey(id)).thenReturn(Optional.of(dto));

    var response = controller.deleteApiKey(id);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void deleteApiKey_missingKey_returns404() {
    var id = UUID.randomUUID();
    when(apiKeyService.deleteApiKey(id)).thenReturn(Optional.empty());

    var response = controller.deleteApiKey(id);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void createApiKey_withoutABody_createsAKeyWithNoLabelOrExpiry() {
    var id = UUID.randomUUID();
    when(apiKeyService.createApiKey(any()))
        .thenReturn(
            new ApiKeyCreatedDto(
                id, id + ":secret", null, LocalDateTime.now(ZoneOffset.UTC), null));

    controller.createApiKey(null);

    verify(apiKeyService).createApiKey(new ApiKeyWriteDto(null, null));
  }

  @Test
  void updateApiKey_existingKey_returns200() {
    var id = UUID.randomUUID();
    var body = new ApiKeyWriteDto("Phones", null);
    var dto = new ApiKeyDto(id, "Phones", LocalDateTime.now(ZoneOffset.UTC), null, null);
    when(apiKeyService.updateApiKey(id, body)).thenReturn(Optional.of(dto));

    var response = controller.updateApiKey(id, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(dto);
  }

  @Test
  void updateApiKey_missingKey_returns404() {
    var id = UUID.randomUUID();
    when(apiKeyService.updateApiKey(any(), any())).thenReturn(Optional.empty());

    var response = controller.updateApiKey(id, new ApiKeyWriteDto(null, null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
