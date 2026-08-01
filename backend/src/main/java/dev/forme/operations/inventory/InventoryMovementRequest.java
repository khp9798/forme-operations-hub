package dev.forme.operations.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InventoryMovementRequest(
        @NotBlank String warehouseCode,
        @NotBlank String skuCode,
        @NotNull InventoryMovementType movementType,
        @Positive int quantity,
        @NotBlank @Size(max = 120) String idempotencyKey,
        @NotBlank @Size(max = 500) String reason
) {
}
