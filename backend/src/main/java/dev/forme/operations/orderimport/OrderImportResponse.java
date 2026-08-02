package dev.forme.operations.orderimport;

import java.util.List;
import java.util.UUID;

public record OrderImportResponse(
        UUID jobId,
        String fileName,
        String status,
        int totalCount,
        int validCount,
        int invalidCount,
        List<OrderImportRowResponse> rows) {
}
