package com.peppeosmio.lockate.config;

import java.lang.reflect.AnnotatedElement;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.annotation.ReflectiveProcessor;

/** Allows registering a class and all its fields and methods for Spring AOT reflection */
public class NativeReflectionProcessor implements ReflectiveProcessor {

  private final BindingReflectionHintsRegistrar registrar = new BindingReflectionHintsRegistrar();

  @Override
  public void registerReflectionHints(ReflectionHints hints, AnnotatedElement element) {
    if (element instanceof Class<?> type) {
      registrar.registerReflectionHints(hints, type);
    }
  }
}
