package com.peppeosmio.lockate.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class AdminAuthInterceptor implements HandlerInterceptor {

  private final String adminApiKey;
  private final ObjectMapper objectMapper;

  public AdminAuthInterceptor(
      @Value("${lockate.admin-api-key}") String adminApiKey, ObjectMapper objectMapper) {
    if (adminApiKey == null || adminApiKey.isBlank()) {
      throw new IllegalStateException(
          "ADMIN_API_KEY environment variable must be set to a non-empty value");
    }
    this.adminApiKey = adminApiKey.trim();
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {

    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }

    boolean secured =
        method.hasMethodAnnotation(SecuredAdmin.class)
            || method.getBeanType().isAnnotationPresent(SecuredAdmin.class);

    if (!secured) {
      return true;
    }

    var apiKeyHeader = request.getHeader("X-API-KEY");
    if (apiKeyHeader == null) {
      response.sendError(
          401, objectMapper.writeValueAsString(new ErrorResponseDto("missing_api_key")));
      return false;
    }

    if (!adminApiKey.equals(apiKeyHeader)) {
      response.sendError(
          401, objectMapper.writeValueAsString(new ErrorResponseDto("invalid_api_key")));
      return false;
    }

    SecurityContextHolder.getContext().setAuthentication(new AdminAuthentication());
    return true;
  }
}
