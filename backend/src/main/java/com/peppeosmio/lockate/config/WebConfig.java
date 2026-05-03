package com.peppeosmio.lockate.config;

import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AGMemberAuthInterceptor agMemberAuthInterceptor;

    public WebConfig(AGMemberAuthInterceptor agMemberAuthInterceptor) {
        this.agMemberAuthInterceptor = agMemberAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(agMemberAuthInterceptor)
                .addPathPatterns("/api/anonymous-groups/**"); // or restrict paths
    }
}
