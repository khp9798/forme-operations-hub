package dev.forme.operations.orderimport;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/order-imports")
public class OrderImportController {
    private final OrderImportService orderImportService;
    private final OrderBatchService orderBatchService;

    public OrderImportController(OrderImportService orderImportService, OrderBatchService orderBatchService) {
        this.orderImportService = orderImportService;
        this.orderBatchService = orderBatchService;
    }

    @PostMapping("/validate")
    @ResponseStatus(HttpStatus.CREATED)
    OrderImportResponse validate(@RequestParam("file") MultipartFile file, Principal principal) {
        return orderImportService.validate(file, principal.getName());
    }

    @PostMapping("/{jobId}/process")
    OrderBatchResponse process(@PathVariable UUID jobId, Principal principal) {
        return orderBatchService.process(jobId, principal.getName());
    }
}
