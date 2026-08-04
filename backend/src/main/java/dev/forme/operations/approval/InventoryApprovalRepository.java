package dev.forme.operations.approval;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.forme.operations.inventory.InventoryMovementType;
import dev.forme.operations.inventory.InventoryNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryApprovalRepository {
    private final JdbcTemplate jdbc;

    public InventoryApprovalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Target findTarget(String warehouseCode, String skuCode) {
        List<Target> rows = jdbc.query("""
                SELECT ip.warehouse_id, ip.sku_id FROM inventory_positions ip
                  JOIN warehouses w ON w.id=ip.warehouse_id JOIN skus s ON s.id=ip.sku_id
                 WHERE w.code=? AND s.sku_code=?
                """, (rs, row) -> new Target(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
                warehouseCode, skuCode);
        if (rows.isEmpty()) throw new InventoryNotFoundException("창고와 SKU에 해당하는 재고가 없습니다.");
        return rows.getFirst();
    }

    void insertRequest(UUID id, Target target, AdjustmentRequestCreate request, String actor) {
        jdbc.update("""
                INSERT INTO inventory_adjustment_requests
                    (id, warehouse_id, sku_id, movement_type, quantity, reason, status, requested_by)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, id, target.warehouseId(), target.skuId(), request.movementType().name(),
                request.quantity(), request.reason(), actor);
    }

    List<AdjustmentRequestResponse> search(AdjustmentStatus status) {
        String value = status == null ? "" : status.name();
        return jdbc.query(baseSelect() + """
                 WHERE (?='' OR ar.status=?)
                 ORDER BY CASE WHEN ar.status='PENDING' THEN 0 ELSE 1 END, ar.requested_at DESC
                """, this::mapRequest, value, value);
    }

    LockedRequest lockRequest(UUID id) {
        List<LockedRequest> rows = jdbc.query("""
                SELECT ar.status, ar.requested_by, ar.movement_type, ar.quantity, ar.reason,
                       w.code AS warehouse_code, s.sku_code
                  FROM inventory_adjustment_requests ar
                  JOIN warehouses w ON w.id=ar.warehouse_id JOIN skus s ON s.id=ar.sku_id
                 WHERE ar.id=? FOR UPDATE OF ar
                """, (rs, row) -> new LockedRequest(AdjustmentStatus.valueOf(rs.getString("status")),
                rs.getString("requested_by"), rs.getString("warehouse_code"), rs.getString("sku_code"),
                InventoryMovementType.valueOf(rs.getString("movement_type")), rs.getInt("quantity"),
                rs.getString("reason")), id);
        if (rows.isEmpty()) throw new ApprovalNotFoundException("승인 요청을 찾을 수 없습니다.");
        return rows.getFirst();
    }

    void decide(UUID id, AdjustmentStatus status, String actor, String comment, UUID movementId) {
        jdbc.update("""
                UPDATE inventory_adjustment_requests
                   SET status=?, decided_by=?, decision_comment=?, decided_at=CURRENT_TIMESTAMP,
                       movement_id=?, version=version+1
                 WHERE id=?
                """, status.name(), actor, comment, movementId, id);
    }

    AdjustmentRequestResponse findById(UUID id) {
        List<AdjustmentRequestResponse> rows = jdbc.query(baseSelect() + " WHERE ar.id=?", this::mapRequest, id);
        if (rows.isEmpty()) throw new ApprovalNotFoundException("승인 요청을 찾을 수 없습니다.");
        return rows.getFirst();
    }

    void writeAudit(String actor, String action, UUID entityId, String summary) {
        jdbc.update("""
                INSERT INTO audit_logs (id, actor, action, entity_type, entity_id, summary)
                VALUES (?, ?, ?, 'INVENTORY_ADJUSTMENT_REQUEST', ?, ?)
                """, UUID.randomUUID(), actor, action, entityId.toString(), summary);
    }

    private String baseSelect() {
        return """
                SELECT ar.id, w.code AS warehouse_code, w.name AS warehouse_name,
                       s.sku_code, p.name AS product_name, ar.movement_type, ar.quantity,
                       ar.reason, ar.status, ar.requested_by, ar.requested_at,
                       ar.decided_by, ar.decision_comment, ar.decided_at, ar.movement_id
                  FROM inventory_adjustment_requests ar
                  JOIN warehouses w ON w.id=ar.warehouse_id JOIN skus s ON s.id=ar.sku_id
                  JOIN products p ON p.id=s.product_id
                """;
    }

    private AdjustmentRequestResponse mapRequest(ResultSet rs, int row) throws SQLException {
        OffsetDateTime decidedAt = rs.getObject("decided_at", OffsetDateTime.class);
        return new AdjustmentRequestResponse(rs.getObject("id", UUID.class), rs.getString("warehouse_code"),
                rs.getString("warehouse_name"), rs.getString("sku_code"), rs.getString("product_name"),
                InventoryMovementType.valueOf(rs.getString("movement_type")), rs.getInt("quantity"),
                rs.getString("reason"), AdjustmentStatus.valueOf(rs.getString("status")),
                rs.getString("requested_by"), rs.getObject("requested_at", OffsetDateTime.class).toInstant(),
                rs.getString("decided_by"), rs.getString("decision_comment"),
                decidedAt == null ? null : decidedAt.toInstant(), rs.getObject("movement_id", UUID.class));
    }

    record Target(UUID warehouseId, UUID skuId) { }
    record LockedRequest(AdjustmentStatus status, String requestedBy, String warehouseCode,
                         String skuCode, InventoryMovementType movementType, int quantity, String reason) { }
}
