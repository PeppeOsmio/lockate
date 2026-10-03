package com.peppeosmio.lockate.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.ObjectMapper;

class AdminAuthInterceptorTest {

  private static final String CONFIGURED_KEY = "test-admin-key";

  private AdminAuthInterceptor interceptor;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  private static class DummyController {
    @SecuredAdmin
    public void securedMethod() {}

    public void unsecuredMethod() {}
  }

  @BeforeEach
  void setUp() {
    interceptor = new AdminAuthInterceptor(CONFIGURED_KEY, new ObjectMapper());
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private HandlerMethod securedHandlerMethod() throws NoSuchMethodException {
    Method method = DummyController.class.getMethod("securedMethod");
    return new HandlerMethod(new DummyController(), method);
  }

  private HandlerMethod unsecuredHandlerMethod() throws NoSuchMethodException {
    Method method = DummyController.class.getMethod("unsecuredMethod");
    return new HandlerMethod(new DummyController(), method);
  }

  @Test
  void preHandle_nonHandlerMethod_passesThrough() throws Exception {
    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isTrue();
  }

  @Test
  void preHandle_unsecuredMethod_passesThroughWithoutAuthenticating() throws Exception {
    boolean result = interceptor.preHandle(request, response, unsecuredHandlerMethod());

    assertThat(result).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void preHandle_missingApiKeyHeader_sendsError401() throws Exception {
    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getErrorMessage()).contains("missing_api_key");
  }

  @Test
  void preHandle_invalidApiKey_sendsError401() throws Exception {
    request.addHeader("X-API-KEY", "wrong-key");

    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getErrorMessage()).contains("invalid_api_key");
  }

  @Test
  void preHandle_validApiKey_setsSecurityContextAndReturnsTrue() throws Exception {
    request.addHeader("X-API-KEY", CONFIGURED_KEY);

    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication())
        .isInstanceOf(AdminAuthentication.class);
  }
}
