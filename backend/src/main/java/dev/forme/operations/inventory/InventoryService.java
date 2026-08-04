package dev.forme.operations.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    public List<InventoryPositionResponse> search(String query, String warehouseCode) {
        String keyword = query == null ? "" : query.trim();
        String warehouse = warehouseCode == null ? "" : warehouseCode.trim();
        return repository.search(keyword, warehouse);
    }

    @Transactional
    public InventoryMovementResponse move(InventoryMovementRequest request, String actor) {
        repository.lockIdempotencyKey(request.idempotencyKey());
        InventoryRepository.ExistingMovement movement = repository.findMovement(request.idempotencyKey());
        InventoryRepository.LockedInventory inventory = repository.lockInventory(request.warehouseCode(), request.skuCode());
        if (movement != null) {
            return response(movement.id(), request, movement.type(), movement.quantity(), inventory, true);
        }

        UpdatedQuantities updated = calculate(inventory, request.movementType(), request.quantity());
        repository.updateInventory(inventory, updated.onHand(), updated.reserved(), updated.damaged());

        UUID movementId = UUID.randomUUID();
        repository.insertMovement(movementId, inventory, request.movementType(), request.quantity(),
                request.idempotencyKey(), request.reason(), actor);

        InventoryRepository.LockedInventory result = new InventoryRepository.LockedInventory(
                inventory.warehouseId(), inventory.skuId(), updated.onHand(), updated.reserved(), updated.damaged());
        return response(movementId, request, request.movementType(), request.quantity(), result, false);
    }

    private UpdatedQuantities calculate(InventoryRepository.LockedInventory current, InventoryMovementType type, int quantity) {
        int onHand = current.onHand();
        int reserved = current.reserved();
        int damaged = current.damaged();
        switch (type) {
            case RECEIPT, ADJUSTMENT_IN -> onHand += quantity;
            case ADJUSTMENT_OUT -> onHand -= quantity;
            case RESERVE -> reserved += quantity;
            case RELEASE -> reserved -= quantity;
            case DAMAGE -> {
                onHand -= quantity;
                damaged += quantity;
            }
        }
        if (onHand < 0 || reserved < 0 || damaged < 0 || reserved > onHand) {
            throw new InventoryConflictException("요청 수량이 현재 재고 상태에서 허용되는 범위를 초과했습니다.");
        }
        return new UpdatedQuantities(onHand, reserved, damaged);
    }

    private InventoryMovementResponse response(UUID movementId, InventoryMovementRequest request,
                                               InventoryMovementType type, int quantity,
                                               InventoryRepository.LockedInventory inventory, boolean idempotent) {
        return new InventoryMovementResponse(movementId, request.warehouseCode(), request.skuCode(), type,
                quantity, inventory.onHand(), inventory.reserved(), inventory.onHand() - inventory.reserved(),
                inventory.damaged(), idempotent);
    }

    private record UpdatedQuantities(int onHand, int reserved, int damaged) { }
}
