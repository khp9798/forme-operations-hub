package dev.forme.operations.orderimport;

import java.math.BigDecimal;
import java.util.List;

public record OrderImportRowResponse(
        int lineNumber,
        String sourceOrderId,
        String skuCode,
        Integer quantity,
        BigDecimal unitPrice,
        String status,
        List<String> errors) {
}
