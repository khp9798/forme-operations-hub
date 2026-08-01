package dev.forme.operations.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final JdbcTemplate jdbcTemplate;

    public InventoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InventoryPositionResponse> search(String query, String warehouseCode) {
        String keyword = query == null ? "" : query.trim();
        String warehouse = warehouseCode == null ? "" : warehouseCode.trim();
        return jdbcTemplate.query("""
                SELECT w.code AS warehouse_code, w.name AS warehouse_name,
                       p.brand_code, p.style_code, p.name AS product_name,
                       s.sku_code, s.color_code, s.size_code,
                       ip.on_hand_quantity, ip.reserved_quantity,
                       ip.on_hand_quantity - ip.reserved_quantity AS available_quantity,
                       ip.damaged_quantity, ip.updated_at
                  FROM inventory_positions ip
                  JOIN warehouses w ON w.id = ip.warehouse_id
                  JOIN skus s ON s.id = ip.sku_id
                  JOIN products p ON p.id = s.product_id
                 WHERE (? = '' OR w.code = ?)
                   AND (? = '' OR s.sku_code ILIKE '%' || ? || '%'
                                OR p.name ILIKE '%' || ? || '%'
                                OR p.style_code ILIKE '%' || ? || '%')
                 ORDER BY w.code, p.brand_code, s.sku_code
                """, this::mapPosition, warehouse, warehouse, keyword, keyword, keyword, keyword);
    }

    @Transactional
    public InventoryMovementResponse move(InventoryMovementRequest request, String actor) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))",
                resultSet -> null, request.idempotencyKey());

        List<ExistingMovement> existing = jdbcTemplate.query("""
                SELECT id, movement_type, quantity
                  FROM inventory_movements
                 WHERE idempotency_key = ?
                """, (rs, rowNum) -> new ExistingMovement(
                rs.getObject("id", UUID.class),
                InventoryMovementType.valueOf(rs.getString("movement_type")),
                rs.getInt("quantity")), request.idempotencyKey());

        LockedInventory inventory = lockInventory(request.warehouseCode(), request.skuCode());
        if (!existing.isEmpty()) {
            ExistingMovement movement = existing.getFirst();
            return response(movement.id(), request, movement.type(), movement.quantity(), inventory, true);
        }

        UpdatedQuantities updated = calculate(inventory, request.movementType(), request.quantity());
        jdbcTemplate.update("""
                UPDATE inventory_positions
                   SET on_hand_quantity = ?, reserved_quantity = ?, damaged_quantity = ?,
                       version = version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE warehouse_id = ? AND sku_id = ?
                """, updated.onHand(), updated.reserved(), updated.damaged(), inventory.warehouseId(), inventory.skuId());

        UUID movementId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_movements
                    (id, warehouse_id, sku_id, movement_type, quantity, reference_type,
                     reference_id, idempotency_key, reason, created_by)
                VALUES (?, ?, ?, ?, ?, 'MANUAL', ?, ?, ?, ?)
                """, movementId, inventory.warehouseId(), inventory.skuId(), request.movementType().name(),
                request.quantity(), request.idempotencyKey(), request.idempotencyKey(), request.reason(), actor);

        LockedInventory result = inventory.withQuantities(updated);
        return response(movementId, request, request.movementType(), request.quantity(), result, false);
    }

    private LockedInventory lockInventory(String warehouseCode, String skuCode) {
        List<LockedInventory> rows = jdbcTemplate.query("""
                SELECT ip.warehouse_id, ip.sku_id, ip.on_hand_quantity,
                       ip.reserved_quantity, ip.damaged_quantity
                  FROM inventory_positions ip
                  JOIN warehouses w ON w.id = ip.warehouse_id
                  JOIN skus s ON s.id = ip.sku_id
                 WHERE w.code = ? AND s.sku_code = ?
                 FOR UPDATE OF ip
                """, (rs, rowNum) -> new LockedInventory(
                rs.getObject("warehouse_id", UUID.class), rs.getObject("sku_id", UUID.class),
                rs.getInt("on_hand_quantity"), rs.getInt("reserved_quantity"),
                rs.getInt("damaged_quantity")), warehouseCode, skuCode);
        if (rows.isEmpty()) {
            throw new InventoryNotFoundException("창고와 SKU에 해당하는 재고가 없습니다.");
        }
        return rows.getFirst();
    }

    private UpdatedQuantities calculate(LockedInventory current, InventoryMovementType type, int quantity) {
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
                                               LockedInventory inventory, boolean idempotent) {
        return new InventoryMovementResponse(movementId, request.warehouseCode(), request.skuCode(), type,
                quantity, inventory.onHand(), inventory.reserved(), inventory.onHand() - inventory.reserved(),
                inventory.damaged(), idempotent);
    }

    private InventoryPositionResponse mapPosition(ResultSet rs, int rowNum) throws SQLException {
        return new InventoryPositionResponse(
                rs.getString("warehouse_code"), rs.getString("warehouse_name"),
                rs.getString("brand_code"), rs.getString("style_code"), rs.getString("product_name"),
                rs.getString("sku_code"), rs.getString("color_code"), rs.getString("size_code"),
                rs.getInt("on_hand_quantity"), rs.getInt("reserved_quantity"),
                rs.getInt("available_quantity"), rs.getInt("damaged_quantity"),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private record ExistingMovement(UUID id, InventoryMovementType type, int quantity) { }
    private record UpdatedQuantities(int onHand, int reserved, int damaged) { }
    private record LockedInventory(UUID warehouseId, UUID skuId, int onHand, int reserved, int damaged) {
        LockedInventory withQuantities(UpdatedQuantities quantities) {
            return new LockedInventory(warehouseId, skuId, quantities.onHand(), quantities.reserved(), quantities.damaged());
        }
    }
}
