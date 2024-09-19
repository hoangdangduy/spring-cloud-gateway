package com.example.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/healthcheck")
public class HealthCheckController {

    @GetMapping(value = "/liveness")
    public ResponseEntity<Object> getLivenessStatus() {
        return ResponseEntity.ok(true);
    }

    @GetMapping(value = "/readiness")
    public ResponseEntity<Object> getReadinessStatus() {
        return ResponseEntity.ok(true);
    }
}
