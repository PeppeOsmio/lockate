package com.peppeosmio.lockate.admin.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.api_key.ApiKeyService;
import com.peppeosmio.lockate.api_key.dto.ApiKeyDto;
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
import org.springframework.data.domain.Pageable;
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
    var dto = new ApiKeyDto(UUID.randomUUID(), LocalDateTime.now(ZoneOffset.UTC), null);
    var page = new PageResponseDto<>(List.of(dto), 0, 20, 1L, 1);
    when(apiKeyService.listApiKeys(any(Pageable.class))).thenReturn(page);

    var result = controller.listApiKeys(0, 20);

    verify(apiKeyService).listApiKeys(any(Pageable.class));
    assertThat(result.items()).hasSize(1);
  }

  @Test
  void deleteApiKey_existingKey_returns204() {
    var id = UUID.randomUUID();
    var dto = new ApiKeyDto(id, LocalDateTime.now(ZoneOffset.UTC), null);
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
}
