package dev.forme.operations.inventory;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InventoryExceptionHandler {

    @ExceptionHandler(InventoryNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(InventoryNotFoundException error) {
        return response(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(InventoryConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(InventoryConflictException error) {
        return response(HttpStatus.CONFLICT, error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException error) {
        return response(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요.");
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(), "message", message, "timestamp", Instant.now().toString()));
    }
}
