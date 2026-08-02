package dev.forme.operations.approval;

import dev.forme.operations.inventory.InventoryMovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdjustmentRequestCreate(
        @NotBlank String warehouseCode,
        @NotBlank String skuCode,
        @NotNull InventoryMovementType movementType,
        @Positive int quantity,
        @NotBlank @Size(max = 500) String reason
) { }
