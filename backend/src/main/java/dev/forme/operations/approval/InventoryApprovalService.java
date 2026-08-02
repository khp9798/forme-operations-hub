package dev.forme.operations.approval;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.forme.operations.inventory.InventoryMovementRequest;
import dev.forme.operations.inventory.InventoryMovementResponse;
import dev.forme.operations.inventory.InventoryMovementType;
import dev.forme.operations.inventory.InventoryNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryApprovalService {

    private final JdbcTemplate jdbcTemplate;
    private final dev.forme.operations.inventory.InventoryService inventoryService;

    public InventoryApprovalService(JdbcTemplate jdbcTemplate,
                                    dev.forme.operations.inventory.InventoryService inventoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public AdjustmentRequestResponse create(AdjustmentRequestCreate request, String actor) {
        Target target = findTarget(request.warehouseCode(), request.skuCode());
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_adjustment_requests
                    (id, warehouse_id, sku_id, movement_type, quantity, reason, status, requested_by)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, requestId, target.warehouseId(), target.skuId(), request.movementType().name(),
                request.quantity(), request.reason(), actor);
        writeAudit(actor, "INVENTORY_ADJUSTMENT_REQUESTED", requestId,
                "%s · %s · %s %d개 조정 요청".formatted(
                        request.warehouseCode(), request.skuCode(), request.movementType(), request.quantity()));
        return findById(requestId);
    }

    public List<AdjustmentRequestResponse> search(AdjustmentStatus status) {
        String statusValue = status == null ? "" : status.name();
        return jdbcTemplate.query(baseSelect() + """
                 WHERE (? = '' OR ar.status = ?)
                 ORDER BY CASE WHEN ar.status = 'PENDING' THEN 0 ELSE 1 END, ar.requested_at DESC
                """, this::mapRequest, statusValue, statusValue);
    }

    @Transactional
    public AdjustmentRequestResponse decide(UUID requestId, ApprovalDecisionRequest decision, String actor) {
        LockedRequest request = lockRequest(requestId);
        if (request.status() != AdjustmentStatus.PENDING) {
            throw new ApprovalConflictException("이미 처리된 승인 요청입니다.");
        }
        if (request.requestedBy().equals(actor)) {
            throw new ApprovalConflictException("요청자와 승인자는 같을 수 없습니다.");
        }

        String comment = decision.comment() == null ? "" : decision.comment().trim();
        UUID movementId = null;
        AdjustmentStatus nextStatus;
        String action;
        if (decision.decision() == ApprovalDecision.APPROVE) {
            InventoryMovementResponse movement = inventoryService.move(new InventoryMovementRequest(
                    request.warehouseCode(), request.skuCode(), request.movementType(), request.quantity(),
                    "approval-" + requestId, request.reason()), actor);
            movementId = movement.movementId();
            nextStatus = AdjustmentStatus.APPROVED;
            action = "INVENTORY_ADJUSTMENT_APPROVED";
        } else {
            if (comment.isBlank()) {
                throw new ApprovalConflictException("거절 사유를 입력해 주세요.");
            }
            nextStatus = AdjustmentStatus.REJECTED;
            action = "INVENTORY_ADJUSTMENT_REJECTED";
        }

        jdbcTemplate.update("""
                UPDATE inventory_adjustment_requests
                   SET status = ?, decided_by = ?, decision_comment = ?, decided_at = CURRENT_TIMESTAMP,
                       movement_id = ?, version = version + 1
                 WHERE id = ?
                """, nextStatus.name(), actor, comment, movementId, requestId);
        writeAudit(actor, action, requestId,
                "%s · %s · 요청자 %s · %s".formatted(
                        request.warehouseCode(), request.skuCode(), request.requestedBy(),
                        comment.isBlank() ? "의견 없음" : comment));
        return findById(requestId);
    }

    private Target findTarget(String warehouseCode, String skuCode) {
        List<Target> rows = jdbcTemplate.query("""
                SELECT ip.warehouse_id, ip.sku_id
                  FROM inventory_positions ip
                  JOIN warehouses w ON w.id = ip.warehouse_id
                  JOIN skus s ON s.id = ip.sku_id
                 WHERE w.code = ? AND s.sku_code = ?
                """, (rs, rowNum) -> new Target(
                rs.getObject("warehouse_id", UUID.class), rs.getObject("sku_id", UUID.class)),
                warehouseCode, skuCode);
        if (rows.isEmpty()) throw new InventoryNotFoundException("창고와 SKU에 해당하는 재고가 없습니다.");
        return rows.getFirst();
    }

    private LockedRequest lockRequest(UUID requestId) {
        List<LockedRequest> rows = jdbcTemplate.query("""
                SELECT ar.status, ar.requested_by, ar.movement_type, ar.quantity, ar.reason,
                       w.code AS warehouse_code, s.sku_code
                  FROM inventory_adjustment_requests ar
                  JOIN warehouses w ON w.id = ar.warehouse_id
                  JOIN skus s ON s.id = ar.sku_id
                 WHERE ar.id = ?
                 FOR UPDATE OF ar
                """, (rs, rowNum) -> new LockedRequest(
                AdjustmentStatus.valueOf(rs.getString("status")), rs.getString("requested_by"),
                rs.getString("warehouse_code"), rs.getString("sku_code"),
                InventoryMovementType.valueOf(rs.getString("movement_type")),
                rs.getInt("quantity"), rs.getString("reason")), requestId);
        if (rows.isEmpty()) throw new ApprovalNotFoundException("승인 요청을 찾을 수 없습니다.");
        return rows.getFirst();
    }

    private AdjustmentRequestResponse findById(UUID id) {
        List<AdjustmentRequestResponse> rows = jdbcTemplate.query(
                baseSelect() + " WHERE ar.id = ?", this::mapRequest, id);
        if (rows.isEmpty()) throw new ApprovalNotFoundException("승인 요청을 찾을 수 없습니다.");
        return rows.getFirst();
    }

    private String baseSelect() {
        return """
                SELECT ar.id, w.code AS warehouse_code, w.name AS warehouse_name,
                       s.sku_code, p.name AS product_name, ar.movement_type, ar.quantity,
                       ar.reason, ar.status, ar.requested_by, ar.requested_at,
                       ar.decided_by, ar.decision_comment, ar.decided_at, ar.movement_id
                  FROM inventory_adjustment_requests ar
                  JOIN warehouses w ON w.id = ar.warehouse_id
                  JOIN skus s ON s.id = ar.sku_id
                  JOIN products p ON p.id = s.product_id
                """;
    }

    private AdjustmentRequestResponse mapRequest(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime decidedAt = rs.getObject("decided_at", OffsetDateTime.class);
        return new AdjustmentRequestResponse(
                rs.getObject("id", UUID.class), rs.getString("warehouse_code"),
                rs.getString("warehouse_name"), rs.getString("sku_code"), rs.getString("product_name"),
                InventoryMovementType.valueOf(rs.getString("movement_type")), rs.getInt("quantity"),
                rs.getString("reason"), AdjustmentStatus.valueOf(rs.getString("status")),
                rs.getString("requested_by"), rs.getObject("requested_at", OffsetDateTime.class).toInstant(),
                rs.getString("decided_by"), rs.getString("decision_comment"),
                decidedAt == null ? null : decidedAt.toInstant(), rs.getObject("movement_id", UUID.class));
    }

    private void writeAudit(String actor, String action, UUID entityId, String summary) {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (id, actor, action, entity_type, entity_id, summary)
                VALUES (?, ?, ?, 'INVENTORY_ADJUSTMENT_REQUEST', ?, ?)
                """, UUID.randomUUID(), actor, action, entityId.toString(), summary);
    }

    private record Target(UUID warehouseId, UUID skuId) { }
    private record LockedRequest(AdjustmentStatus status, String requestedBy, String warehouseCode,
                                 String skuCode, InventoryMovementType movementType, int quantity, String reason) { }
}
