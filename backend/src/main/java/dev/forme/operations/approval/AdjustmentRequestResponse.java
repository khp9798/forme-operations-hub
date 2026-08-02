package dev.forme.operations.approval;

import java.time.Instant;
import java.util.UUID;

import dev.forme.operations.inventory.InventoryMovementType;

public record AdjustmentRequestResponse(
        UUID id,
        String warehouseCode,
        String warehouseName,
        String skuCode,
        String productName,
        InventoryMovementType movementType,
        int quantity,
        String reason,
        AdjustmentStatus status,
        String requestedBy,
        Instant requestedAt,
        String decidedBy,
        String decisionComment,
        Instant decidedAt,
        UUID movementId
) { }
