package dev.forme.operations.operationsbatch;

import java.time.Instant;
import java.util.UUID;

public record RejectedOrderItemResponse(
        UUID id,
        String sourceOrderId,
        String skuCode,
        String productName,
        String errorCodes,
        String resolutionStatus,
        Instant rejectedAt,
        Instant resolvedAt) { }

