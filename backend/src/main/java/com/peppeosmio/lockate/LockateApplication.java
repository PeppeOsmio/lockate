package com.peppeosmio.lockate;

import com.peppeosmio.lockate.config.NativeRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(NativeRuntimeHints.class)
public class LockateApplication {

  public static void main(String[] args) {
    SpringApplication.run(LockateApplication.class, args);
  }
}
