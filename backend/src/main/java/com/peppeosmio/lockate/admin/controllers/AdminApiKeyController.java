package com.peppeosmio.lockate.admin.controllers;

import com.peppeosmio.lockate.admin.security.SecuredAdmin;
import com.peppeosmio.lockate.api_key.ApiKeyService;
import com.peppeosmio.lockate.api_key.dto.ApiKeyCreatedDto;
import com.peppeosmio.lockate.api_key.dto.ApiKeyDto;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import com.peppeosmio.lockate.common.dto.PageResponseDto;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  PageResponseDto<ApiKeyDto> listApiKeys(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return apiKeyService.listApiKeys(PageRequest.of(page, size, Sort.by("createdAt").descending()));
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
