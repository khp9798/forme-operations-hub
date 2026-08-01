package dev.forme.operations.inventory;

import java.time.Instant;

public record InventoryPositionResponse(
        String warehouseCode,
        String warehouseName,
        String brandCode,
        String styleCode,
        String productName,
        String skuCode,
        String colorCode,
        String sizeCode,
        int onHandQuantity,
        int reservedQuantity,
        int availableQuantity,
        int damagedQuantity,
        Instant updatedAt
) {
}
