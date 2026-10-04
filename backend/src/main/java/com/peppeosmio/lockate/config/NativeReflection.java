package com.peppeosmio.lockate.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.aot.hint.annotation.Reflective;

/** Marks a type that is passed to the ObjectMapper manually, which Spring AOT cannot detect. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Reflective(NativeReflectionProcessor.class)
public @interface NativeReflection {}
