package com.peppeosmio.lockate.config;

import com.peppeosmio.lockate.admin.security.AdminAuthInterceptor;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AGMemberAuthInterceptor agMemberAuthInterceptor;
  private final AdminAuthInterceptor adminAuthInterceptor;

  @Value("${lockate.admin-ui-origin}")
  private String adminUiOrigin;

  public WebConfig(
      AGMemberAuthInterceptor agMemberAuthInterceptor, AdminAuthInterceptor adminAuthInterceptor) {
    this.agMemberAuthInterceptor = agMemberAuthInterceptor;
    this.adminAuthInterceptor = adminAuthInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(agMemberAuthInterceptor)
        .addPathPatterns("/api/anonymous-groups/**"); // or restrict paths
    registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api/admin/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/admin/**")
        .allowedOrigins(adminUiOrigin)
        .allowedMethods("GET", "POST", "DELETE")
        .allowedHeaders("X-API-KEY", "Content-Type");
  }
}
