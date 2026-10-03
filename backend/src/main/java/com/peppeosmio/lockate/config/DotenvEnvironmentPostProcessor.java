package com.peppeosmio.lockate.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Path dotenvPath = Path.of(".env");
    if (!Files.exists(dotenvPath)) {
      return;
    }

    Map<String, Object> properties = new HashMap<>();
    try {
      for (String line : Files.readAllLines(dotenvPath)) {
        line = line.strip();
        if (line.isEmpty() || line.startsWith("#")) continue;
        int eq = line.indexOf('=');
        if (eq <= 0) continue;
        String key = line.substring(0, eq).strip();
        String value = line.substring(eq + 1).strip();
        properties.put(key, value);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read .env file: " + dotenvPath.toAbsolutePath(), e);
    }

    if (!properties.isEmpty()) {
      // addLast = lowest priority; system env vars and -D flags override .env values
      environment.getPropertySources().addLast(new MapPropertySource("dotenv", properties));
    }
  }
}
