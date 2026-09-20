package com.peppeosmio.lockate.admin.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.api_key.ApiKeyService;
import com.peppeosmio.lockate.api_key.dto.ApiKeyDto;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
