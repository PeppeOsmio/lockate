package com.peppeosmio.lockate.admin.controllers;

import com.peppeosmio.lockate.admin.security.SecuredAdmin;
import com.peppeosmio.lockate.api_key.ApiKeyService;
import com.peppeosmio.lockate.api_key.dto.ApiKeyCreatedDto;
import com.peppeosmio.lockate.api_key.dto.ApiKeyDto;
import com.peppeosmio.lockate.api_key.dto.ApiKeyWriteDto;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import com.peppeosmio.lockate.common.dto.PageQueryDto;
import com.peppeosmio.lockate.common.dto.PageResponseDto;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@SecuredAdmin
@RestController
@RequestMapping("/api/v1/admin/api-keys")
public class AdminApiKeyController {

  private final ApiKeyService apiKeyService;

  public AdminApiKeyController(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @GetMapping("")
  @ResponseStatus(HttpStatus.OK)
  PageResponseDto<ApiKeyDto> listApiKeys(@Valid @ModelAttribute PageQueryDto query) {
    return apiKeyService.listApiKeys(query);
  }

  @PostMapping("")
  @ResponseStatus(HttpStatus.CREATED)
  ApiKeyCreatedDto createApiKey(@Valid @RequestBody(required = false) ApiKeyWriteDto request) {
    var created =
        apiKeyService.createApiKey(request == null ? new ApiKeyWriteDto(null, null) : request);
    log.info("[Admin] Created API key {} (expires {})", created.id(), expiry(created.expiresAt()));
    return created;
  }

  @PatchMapping("/{id}")
  ResponseEntity<?> updateApiKey(
      @PathVariable UUID id, @Valid @RequestBody ApiKeyWriteDto request) {
    return apiKeyService
        .updateApiKey(id, request)
        .<ResponseEntity<?>>map(
            dto -> {
              log.info("[Admin] Updated API key {} (expires {})", id, expiry(dto.expiresAt()));
              return ResponseEntity.ok(dto);
            })
        .orElseGet(
            () ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponseDto("api_key_not_found")));
  }

  @DeleteMapping("/{id}")
  ResponseEntity<?> deleteApiKey(@PathVariable UUID id) {
    return apiKeyService
        .deleteApiKey(id)
        .<ResponseEntity<?>>map(
            dto -> {
              log.info("[Admin] Revoked API key {}", id);
              return ResponseEntity.noContent().build();
            })
        .orElseGet(
            () ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponseDto("api_key_not_found")));
  }

  private static Object expiry(LocalDateTime expiresAt) {
    return expiresAt == null ? "never" : expiresAt;
  }
}
