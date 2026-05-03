package com.peppeosmio.lockate.anonymous_group.configuration_properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "lockate.retention.anonymous-group.location")
@Getter
@Setter
public class AGLocationConfigurationProperties {
    String cron;
    Duration duration;
    Duration saveInterval;
}

