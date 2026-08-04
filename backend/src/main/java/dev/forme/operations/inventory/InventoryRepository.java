package dev.forme.operations.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<InventoryPositionResponse> search(String keyword, String warehouse) {
        return jdbc.query("""
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

    void lockIdempotencyKey(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", resultSet -> null, key);
    }

    ExistingMovement findMovement(String key) {
        List<ExistingMovement> rows = jdbc.query("""
                SELECT id, movement_type, quantity FROM inventory_movements WHERE idempotency_key=?
                """, (rs, rowNum) -> new ExistingMovement(rs.getObject("id", UUID.class),
                InventoryMovementType.valueOf(rs.getString("movement_type")), rs.getInt("quantity")), key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    LockedInventory lockInventory(String warehouseCode, String skuCode) {
        List<LockedInventory> rows = jdbc.query("""
                SELECT ip.warehouse_id, ip.sku_id, ip.on_hand_quantity,
                       ip.reserved_quantity, ip.damaged_quantity
                  FROM inventory_positions ip
                  JOIN warehouses w ON w.id = ip.warehouse_id
                  JOIN skus s ON s.id = ip.sku_id
                 WHERE w.code=? AND s.sku_code=?
                 FOR UPDATE OF ip
                """, (rs, rowNum) -> new LockedInventory(rs.getObject("warehouse_id", UUID.class),
                rs.getObject("sku_id", UUID.class), rs.getInt("on_hand_quantity"),
                rs.getInt("reserved_quantity"), rs.getInt("damaged_quantity")), warehouseCode, skuCode);
        if (rows.isEmpty()) throw new InventoryNotFoundException("창고와 SKU에 해당하는 재고가 없습니다.");
        return rows.getFirst();
    }

    void updateInventory(LockedInventory inventory, int onHand, int reserved, int damaged) {
        jdbc.update("""
                UPDATE inventory_positions
                   SET on_hand_quantity=?, reserved_quantity=?, damaged_quantity=?,
                       version=version+1, updated_at=CURRENT_TIMESTAMP
                 WHERE warehouse_id=? AND sku_id=?
                """, onHand, reserved, damaged, inventory.warehouseId(), inventory.skuId());
    }

    void insertMovement(UUID id, LockedInventory inventory, InventoryMovementType type, int quantity,
                        String idempotencyKey, String reason, String actor) {
        jdbc.update("""
                INSERT INTO inventory_movements
                    (id, warehouse_id, sku_id, movement_type, quantity, reference_type,
                     reference_id, idempotency_key, reason, created_by)
                VALUES (?, ?, ?, ?, ?, 'MANUAL', ?, ?, ?, ?)
                """, id, inventory.warehouseId(), inventory.skuId(), type.name(), quantity,
                idempotencyKey, idempotencyKey, reason, actor);
    }

    private InventoryPositionResponse mapPosition(ResultSet rs, int rowNum) throws SQLException {
        return new InventoryPositionResponse(rs.getString("warehouse_code"), rs.getString("warehouse_name"),
                rs.getString("brand_code"), rs.getString("style_code"), rs.getString("product_name"),
                rs.getString("sku_code"), rs.getString("color_code"), rs.getString("size_code"),
                rs.getInt("on_hand_quantity"), rs.getInt("reserved_quantity"),
                rs.getInt("available_quantity"), rs.getInt("damaged_quantity"),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    record ExistingMovement(UUID id, InventoryMovementType type, int quantity) { }
    record LockedInventory(UUID warehouseId, UUID skuId, int onHand, int reserved, int damaged) { }
}
