package dev.forme.operations.operationsbatch;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OperationalBatchExceptionHandler {
    @ExceptionHandler(OperationalBatchNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(OperationalBatchNotFoundException error) {
        return response(HttpStatus.NOT_FOUND, error.getMessage());
    }
    @ExceptionHandler(OperationalBatchConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(OperationalBatchConflictException error) {
        return response(HttpStatus.CONFLICT, error.getMessage());
    }
    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message,
                "timestamp", Instant.now().toString()));
    }
}
