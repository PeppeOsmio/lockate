package com.peppeosmio.lockate;

import com.peppeosmio.lockate.config.NativeRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.ReflectiveScan;

@SpringBootApplication
@ImportRuntimeHints(NativeRuntimeHints.class)
@ReflectiveScan("com.peppeosmio.lockate")
public class LockateApplication {

  public static void main(String[] args) {
    SpringApplication.run(LockateApplication.class, args);
  }
}
