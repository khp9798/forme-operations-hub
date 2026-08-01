package dev.forme.operations.inventory;

import java.util.UUID;

public record InventoryMovementResponse(
        UUID movementId,
        String warehouseCode,
        String skuCode,
        InventoryMovementType movementType,
        int quantity,
        int onHandQuantity,
        int reservedQuantity,
        int availableQuantity,
        int damagedQuantity,
        boolean idempotent
) {
}
