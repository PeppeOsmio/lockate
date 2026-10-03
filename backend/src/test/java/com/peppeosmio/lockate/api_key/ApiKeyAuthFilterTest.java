package com.peppeosmio.lockate.api_key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

  @Mock private ApiKeyService apiKeyService;
  @Mock private FilterChain filterChain;

  private ApiKeyAuthFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    filter = new ApiKeyAuthFilter(apiKeyService, new ObjectMapper());
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @Test
  void doFilterInternal_missingHeader_returns401() throws Exception {
    request.setRequestURI("/api/anonymous-groups");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("missing_api_key");
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternal_invalidHeader_returns401() throws Exception {
    request.setRequestURI("/api/anonymous-groups");
    request.addHeader("X-API-KEY", "not-valid");
    when(apiKeyService.verifyApiKey("not-valid")).thenReturn(false);

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("invalid_api_key");
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternal_whitelistedPath_bypassesApiKeyCheck() throws Exception {
    request.setRequestURI("/api/health");

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(apiKeyService, never()).verifyApiKey(any());
  }

  @Test
  void doFilterInternal_validKey_callsFilterChain() throws Exception {
    var header = UUID.randomUUID() + ":somesecret";
    request.setRequestURI("/api/anonymous-groups");
    request.addHeader("X-API-KEY", header);
    when(apiKeyService.verifyApiKey(header)).thenReturn(true);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_invalidKey_returns401() throws Exception {
    var header = UUID.randomUUID() + ":wrongsecret";
    request.setRequestURI("/api/anonymous-groups");
    request.addHeader("X-API-KEY", header);
    when(apiKeyService.verifyApiKey(header)).thenReturn(false);

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("invalid_api_key");
    verify(filterChain, never()).doFilter(any(), any());
  }
}
