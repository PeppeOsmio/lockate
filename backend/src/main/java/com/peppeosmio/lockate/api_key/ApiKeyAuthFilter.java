package com.peppeosmio.lockate.api_key;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "lockate.require-api-key", havingValue = "true")
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var path = request.getRequestURI();
        // Skip API key check for this endpoint
        var whitelistPaths = List.of("/api/api-key/required", "/api/health");
        if (whitelistPaths.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        var apiKey = request.getHeader("X-API-KEY");
        if (apiKey == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("content-type", "application/json");
            response.getWriter()
                    .write(
                            objectMapper.writeValueAsString(
                                    new ErrorResponseDto("missing_api_key")));
            return;
        }
        try {
            if (apiKeyService.verifyApiKey(UUID.fromString(apiKey))) {
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("content-type", "application/json");
            response.getWriter()
                    .write(
                            objectMapper.writeValueAsString(
                                    new ErrorResponseDto("invalid_api_key")));
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("content-type", "application/json");
            response.getWriter()
                    .write(
                            objectMapper.writeValueAsString(
                                    new ErrorResponseDto("invalid_api_key")));
        }
    }
}
