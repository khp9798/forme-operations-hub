package dev.forme.operations.system;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    @GetMapping("/info")
    ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
                "service", "forme-operations-hub",
                "status", "UP",
                "time", Instant.now().toString(),
                "modules", List.of("inventory", "order-integration", "batch", "audit", "ai-assistant")
        ));
    }
}
