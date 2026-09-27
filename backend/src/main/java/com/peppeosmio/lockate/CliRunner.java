package com.peppeosmio.lockate;

import com.peppeosmio.lockate.api_key.ApiKeyService;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("cli")
@Component
public class CliRunner implements CommandLineRunner {

  private final ApiKeyService apiKeyService;

  public CliRunner(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @Override
  public void run(String... args) {
    String action = args.length > 0 ? args[0] : null;

    switch (action) {
      case "create-api-key" -> {
        var apiKeyDto = apiKeyService.createApiKey();
        System.out.println("\n\n----------------------------------------------");
        System.out.println("Created API key: " + apiKeyDto.key());
        System.out.println("----------------------------------------------\n\n");
        System.exit(0);
      }

      case "list-api-keys" -> {
        var apiKeyPage = apiKeyService.listApiKeys(
            PageRequest.of(0, 1000, Sort.by("createdAt").descending()));
        var apiKeyDtos = apiKeyPage.items();
        System.out.println("\n\n----------------------------------------------");
        for (int i = 0; i < apiKeyDtos.size(); i++) {
          var apiKeyDto = apiKeyDtos.get(i);
          System.out.println((i + 1) + " | " + apiKeyDto.createdAt() + " | " + apiKeyDto.id());
        }
        System.out.println("----------------------------------------------\n\n");
        System.exit(0);
      }

      case "delete-api-key" -> {
        if (args.length < 2) {
          System.out.println("\n\n----------------------------------------------");
          System.out.println("Please specify the api key to delete");
          System.out.println("----------------------------------------------\n\n");
          System.exit(1);
          return;
        }
        var apiKey = args[1];
        var deletedApiKey = apiKeyService.deleteApiKey(UUID.fromString(apiKey)).orElse(null);
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
