package dev.forme.operations.analytics;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {SalesAnalyticsController.class, WarehouseAnalyticsController.class})
public class AnalyticsExceptionHandler {
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> state(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(AnalyticsServiceUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(AnalyticsServiceUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", exception.getMessage()));
    }
}
