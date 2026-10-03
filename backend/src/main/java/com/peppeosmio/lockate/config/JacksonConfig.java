package com.peppeosmio.lockate.config;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {
  @Bean
  public JsonMapper objectMapper() {
    var localDateTimeModule =
        new SimpleModule()
            .addSerializer(
                LocalDateTime.class,
                new LocalDateTimeSerializer(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
                        .withZone(ZoneOffset.UTC)));
    return JsonMapper.builder()
        .addModule(localDateTimeModule)
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
  }
}
