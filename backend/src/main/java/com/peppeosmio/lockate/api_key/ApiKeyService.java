package com.peppeosmio.lockate.api_key;

import com.peppeosmio.lockate.api_key.dto.ApiKeyDto;

import jakarta.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    public ApiKeyDto createApiKey() {
        var apiKeyEntity =
                apiKeyRepository.save(new ApiKeyEntity(LocalDateTime.now(ZoneOffset.UTC)));
        return new ApiKeyDto(apiKeyEntity.getKey(), apiKeyEntity.getCreatedAt());
    }

    @Transactional
    public boolean verifyApiKey(UUID key) {
        var apiKeyEntity = apiKeyRepository.findById(key);
        if (apiKeyEntity.isEmpty()) {
            return false;
        }
        apiKeyEntity.get().setLastValidated(LocalDateTime.now(ZoneOffset.UTC));
        return true;
    }

    public List<ApiKeyDto> listApiKeys() {
        var entities = apiKeyRepository.findAll().iterator();
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(entities, Spliterator.ORDERED), false)
                .map(entity -> new ApiKeyDto(entity.getKey(), entity.getCreatedAt()))
                .toList();
    }

    public Optional<ApiKeyDto> deleteApiKey(UUID apiKey) {
        var apiKeyEntity = apiKeyRepository.findById(apiKey).orElse(null);
        if (apiKeyEntity == null) {
            return Optional.empty();
        }
        apiKeyRepository.deleteById(apiKey);
        return Optional.of(new ApiKeyDto(apiKeyEntity.getKey(), apiKeyEntity.getCreatedAt()));
    }
}
