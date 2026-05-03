package com.peppeosmio.lockate.api_key;

import com.peppeosmio.lockate.api_key.dto.ApiKeyRequiredDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/api-key")
public class ApiKeyController {

    private @Value("${lockate.require-api-key:false}") Boolean requireApiKey;

    @GetMapping("/test")
    public void testApiKey() {
    }

    @GetMapping("/required")
    public ApiKeyRequiredDto apiKeyRequired() {
        return new ApiKeyRequiredDto(requireApiKey);
    }
}
