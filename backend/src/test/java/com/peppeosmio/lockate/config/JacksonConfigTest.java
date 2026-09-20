package com.peppeosmio.lockate.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JacksonConfigTest {

  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
      new JacksonConfig().objectMapper();

  @Test
  void serialize_localDateTime_usesMicrosecondUtcPattern() throws Exception {
    var dateTime = LocalDateTime.of(2026, 7, 12, 10, 30, 45, 123456000);

    var json = objectMapper.writeValueAsString(dateTime);

    assertThat(json).isEqualTo("\"2026-07-12T10:30:45.123456\"");
  }

  @Test
  void serializeAndDeserialize_presentOptional_roundTrips() throws Exception {
    Optional<String> value = Optional.of("hello");

    var json = objectMapper.writeValueAsString(value);
    var javaType =
        objectMapper.getTypeFactory().constructParametricType(Optional.class, String.class);
    Optional<?> deserialized = objectMapper.readValue(json, javaType);

    assertThat(deserialized).isEqualTo(Optional.of("hello"));
  }

  @Test
  void serializeAndDeserialize_emptyOptional_roundTrips() throws Exception {
    Optional<String> value = Optional.empty();

    var json = objectMapper.writeValueAsString(value);
    var javaType =
        objectMapper.getTypeFactory().constructParametricType(Optional.class, String.class);
    Optional<?> deserialized = objectMapper.readValue(json, javaType);

    assertThat(deserialized).isEmpty();
  }
}
