package com.peppeosmio.lockate.admin.controllers;

import com.peppeosmio.lockate.admin.security.SecuredAdmin;
import com.peppeosmio.lockate.api_key.ApiKeyService;
import com.peppeosmio.lockate.api_key.dto.ApiKeyCreatedDto;
import com.peppeosmio.lockate.api_key.dto.ApiKeyDto;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@SecuredAdmin
@RestController
@RequestMapping("/api/admin/api-keys")
public class AdminApiKeyController {

  private final ApiKeyService apiKeyService;

  public AdminApiKeyController(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @GetMapping("")
  @ResponseStatus(HttpStatus.OK)
  List<ApiKeyDto> listApiKeys() {
    return apiKeyService.listApiKeys();
  }

  @PostMapping("")
  @ResponseStatus(HttpStatus.CREATED)
  ApiKeyCreatedDto createApiKey() {
    return apiKeyService.createApiKey();
  }

  @DeleteMapping("/{id}")
  ResponseEntity<?> deleteApiKey(@PathVariable UUID id) {
    return apiKeyService
        .deleteApiKey(id)
        .<ResponseEntity<?>>map(dto -> ResponseEntity.noContent().build())
        .orElseGet(
            () ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponseDto("api_key_not_found")));
  }
}
