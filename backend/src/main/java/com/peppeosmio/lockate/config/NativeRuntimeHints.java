package com.peppeosmio.lockate.config;

import java.util.UUID;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class NativeRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    // Hibernate instantiates an array of the entity id type reflectively to build its multi-id
    // loader
    hints.reflection().registerType(UUID[].class);
  }
}
