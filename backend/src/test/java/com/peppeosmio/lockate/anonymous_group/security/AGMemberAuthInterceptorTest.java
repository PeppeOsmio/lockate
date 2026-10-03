package com.peppeosmio.lockate.anonymous_group.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.controllers.SecuredAGMember;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AGMemberAuthInterceptorTest {

  @Mock private AGMemberAuthenticator agMemberAuthenticator;

  private AGMemberAuthInterceptor interceptor;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  private static class DummyController {
    @SecuredAGMember
    public void securedMethod() {}

    public void unsecuredMethod() {}
  }

  @BeforeEach
  void setUp() {
    interceptor = new AGMemberAuthInterceptor(agMemberAuthenticator, new ObjectMapper());
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
    verify(agMemberAuthenticator, never()).authenticate(any(), any());
  }

  @Test
  void preHandle_missingPathVariables_returnsFalse() throws Exception {
    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isFalse();
  }

  @Test
  void preHandle_pathVariablesMissingGroupId_returnsFalse() throws Exception {
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("otherId", "123"));

    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isFalse();
  }

  @Test
  void preHandle_missingAuthorizationHeader_sendsError400() throws Exception {
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        Map.of("anonymousGroupId", UUID.randomUUID().toString()));

    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void preHandle_malformedGroupIdUuid_sendsError400() throws Exception {
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("anonymousGroupId", "not-a-uuid"));
    request.addHeader("Authorization", "AGMember " + UUID.randomUUID() + " token");

    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void preHandle_agNotFoundException_sendsError404WithJsonBody() throws Exception {
    var groupId = UUID.randomUUID();
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        Map.of("anonymousGroupId", groupId.toString()));
    request.addHeader("Authorization", "AGMember " + UUID.randomUUID() + " token");
    when(agMemberAuthenticator.authenticate(any(), any()))
        .thenThrow(new AGNotFoundException(groupId));

    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getErrorMessage()).contains("ag_not_found");
  }

  @Test
  void preHandle_unauthorizedException_sendsError401WithJsonBody() throws Exception {
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        Map.of("anonymousGroupId", UUID.randomUUID().toString()));
    request.addHeader("Authorization", "AGMember " + UUID.randomUUID() + " token");
    when(agMemberAuthenticator.authenticate(any(), any())).thenThrow(new UnauthorizedException());

    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getErrorMessage()).contains("unauthorized");
  }

  @Test
  void preHandle_success_setsSecurityContextAndReturnsTrue() throws Exception {
    var memberId = UUID.randomUUID();
    var authentication = new AGMemberAuthentication(memberId);
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        Map.of("anonymousGroupId", UUID.randomUUID().toString()));
    request.addHeader("Authorization", "AGMember " + memberId + " token");
    when(agMemberAuthenticator.authenticate(any(), any())).thenReturn(authentication);

    boolean result = interceptor.preHandle(request, response, securedHandlerMethod());

    assertThat(result).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
  }
}
