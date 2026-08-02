package dev.forme.operations.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import dev.forme.operations.inventory.InventoryMovementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class InventoryApprovalIntegrationTest {
    private static final String WAREHOUSE = "ICN-01";
    private static final String SKU = "MLB-CAP-0091-BK-F";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("forme_ops_approval_test").withUsername("forme_test").withPassword("forme_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired InventoryApprovalService service;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetWorkflow() {
        jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_type = 'INVENTORY_ADJUSTMENT_REQUEST'");
        jdbcTemplate.update("DELETE FROM inventory_adjustment_requests");
        jdbcTemplate.update("DELETE FROM inventory_movements WHERE idempotency_key LIKE 'approval-%'");
        jdbcTemplate.update("""
                UPDATE inventory_positions ip
                   SET on_hand_quantity = 10, reserved_quantity = 0, damaged_quantity = 0, version = 0
                  FROM warehouses w, skus s
                 WHERE ip.warehouse_id = w.id AND ip.sku_id = s.id
                   AND w.code = ? AND s.sku_code = ?
                """, WAREHOUSE, SKU);
    }

    @Test
    void appliesStockOnlyAfterAnotherUserApprovesAndWritesAuditTrail() {
        AdjustmentRequestResponse created = service.create(new AdjustmentRequestCreate(
                WAREHOUSE, SKU, InventoryMovementType.ADJUSTMENT_IN, 4, "실물 재고 정기 검수"), "operator-a");

        assertEquals(AdjustmentStatus.PENDING, created.status());
        assertEquals(10, onHandQuantity());
        assertEquals(1, auditCount(created.id()));
        assertThrows(ApprovalConflictException.class, () -> service.decide(created.id(),
                new ApprovalDecisionRequest(ApprovalDecision.APPROVE, "본인 승인"), "operator-a"));
        assertEquals(10, onHandQuantity());

        AdjustmentRequestResponse approved = service.decide(created.id(),
                new ApprovalDecisionRequest(ApprovalDecision.APPROVE, "검수표 확인 완료"), "approver-b");

        assertEquals(AdjustmentStatus.APPROVED, approved.status());
        assertEquals("approver-b", approved.decidedBy());
        assertEquals(14, onHandQuantity());
        assertEquals(2, auditCount(created.id()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE idempotency_key = ?", Integer.class,
                "approval-" + created.id()));
        assertThrows(ApprovalConflictException.class, () -> service.decide(created.id(),
                new ApprovalDecisionRequest(ApprovalDecision.APPROVE, "중복 승인"), "approver-c"));
        assertEquals(14, onHandQuantity());
    }

    @Test
    void requiresCommentToRejectAndDoesNotChangeStock() {
        AdjustmentRequestResponse created = service.create(new AdjustmentRequestCreate(
                WAREHOUSE, SKU, InventoryMovementType.ADJUSTMENT_OUT, 2, "재고 차이 조정"), "operator-a");
        assertThrows(ApprovalConflictException.class, () -> service.decide(created.id(),
                new ApprovalDecisionRequest(ApprovalDecision.REJECT, ""), "approver-b"));

        AdjustmentRequestResponse rejected = service.decide(created.id(),
                new ApprovalDecisionRequest(ApprovalDecision.REJECT, "증빙 자료가 없습니다."), "approver-b");
        assertEquals(AdjustmentStatus.REJECTED, rejected.status());
        assertEquals(10, onHandQuantity());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE idempotency_key = ?", Integer.class,
                "approval-" + created.id()));
    }

    private int onHandQuantity() {
        return jdbcTemplate.queryForObject("""
                SELECT ip.on_hand_quantity FROM inventory_positions ip
                  JOIN warehouses w ON w.id = ip.warehouse_id JOIN skus s ON s.id = ip.sku_id
                 WHERE w.code = ? AND s.sku_code = ?
                """, Integer.class, WAREHOUSE, SKU);
    }

    private int auditCount(UUID requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ?", Integer.class, requestId.toString());
    }
}
