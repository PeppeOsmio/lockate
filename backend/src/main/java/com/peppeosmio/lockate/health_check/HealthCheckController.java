package com.peppeosmio.lockate.health_check;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/api/health")
public class HealthCheckController {
    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    void healthCheck() {}
}
