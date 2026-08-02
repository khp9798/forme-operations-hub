package dev.forme.operations.approval;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApprovalExceptionHandler {

    @ExceptionHandler(ApprovalNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(ApprovalNotFoundException error) {
        return response(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(ApprovalConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ApprovalConflictException error) {
        return response(HttpStatus.CONFLICT, error.getMessage());
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(), "message", message, "timestamp", Instant.now().toString()));
    }
}
