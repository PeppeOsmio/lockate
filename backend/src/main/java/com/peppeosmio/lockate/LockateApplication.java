package com.peppeosmio.lockate;

import com.peppeosmio.lockate.api_key.ApiKeyService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.UUID;

@Slf4j
@SpringBootApplication
public class LockateApplication implements CommandLineRunner {

    private final ApiKeyService apiKeyService; // example bean

    public LockateApplication(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    public static void main(String[] args) {
        SpringApplication.run(LockateApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String action = null;

        if (args.length > 1) {
            action = args[1];
        }

        switch (action) {
            case "create-api-key" -> {
                var apiKeyDto = apiKeyService.createApiKey();
                System.out.println("\n\n----------------------------------------------");
                System.out.println("Created API key: " + apiKeyDto.key());
                System.out.println("----------------------------------------------\n\n");
                System.exit(0);
            }

            case "list-api-keys" -> {
                var apiKeyDtos = apiKeyService.listApiKeys();
                System.out.println("\n\n----------------------------------------------");
                for (int i = 0; i < apiKeyDtos.size(); i++) {
                    var apiKeyDto = apiKeyDtos.get(i);
                    System.out.println(
                            (i + 1) + " | " + apiKeyDto.createdAt() + " | " + apiKeyDto.key());
                }
                System.out.println("----------------------------------------------\n\n");
                System.exit(0);
            }

            // ./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=cli create-api-key"
            case "delete-api-key" -> {
                if (args.length < 2) {
                    System.out.println("\n\n----------------------------------------------");
                    System.out.println("Please specify the api key to delete");
                    System.out.println("----------------------------------------------\n\n");
                    System.exit(1);
                }
                var apiKey = args[1];
                var deletedApiKey =
                        apiKeyService.deleteApiKey(UUID.fromString(apiKey)).orElse(null);
                if (deletedApiKey == null) {
                    System.out.println("\n\n----------------------------------------------");
                    System.out.println("Api key not found " + apiKey);
                    System.out.println("----------------------------------------------\n\n");
                    System.exit(1);
                    return;
                }
                System.out.println("\n\n----------------------------------------------");
                System.out.println("Deleted api key " + apiKey);
                System.out.println("----------------------------------------------\n\n");
                System.exit(0);
            }

            case null, default -> {}
        }
    }
}
