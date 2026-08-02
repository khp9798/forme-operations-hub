package dev.forme.operations.orderimport;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/order-imports")
public class OrderImportController {
    private final OrderImportService orderImportService;

    public OrderImportController(OrderImportService orderImportService) {
        this.orderImportService = orderImportService;
    }

    @PostMapping("/validate")
    @ResponseStatus(HttpStatus.CREATED)
    OrderImportResponse validate(@RequestParam("file") MultipartFile file, Principal principal) {
        return orderImportService.validate(file, principal.getName());
    }
}
