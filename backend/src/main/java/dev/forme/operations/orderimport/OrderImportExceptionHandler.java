package dev.forme.operations.orderimport;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrderImportController.class)
public class OrderImportExceptionHandler {
    @ExceptionHandler(OrderImportValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidFile(OrderImportValidationException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    record ErrorResponse(String message) { }
}
