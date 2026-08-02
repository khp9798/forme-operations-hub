package dev.forme.operations.orderimport.batch;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StagedOrderRow(
        UUID rowId,
        UUID importJobId,
        String sourceOrderId,
        OffsetDateTime orderedAt,
        UUID skuId,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        String recipientName,
        String postalCode,
        String addressLine1,
        String addressLine2) {
}
