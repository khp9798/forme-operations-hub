package dev.forme.operations.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
class InventoryConcurrencyIntegrationTest {

    private static final String WAREHOUSE = "ICN-01";
    private static final String SKU = "MLB-CAP-0091-BK-F";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("forme_ops_test")
            .withUsername("forme_test")
            .withPassword("forme_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetInventory() {
        jdbcTemplate.update("DELETE FROM inventory_movements WHERE idempotency_key LIKE 'it-%'");
        jdbcTemplate.update("""
                UPDATE inventory_positions ip
                   SET on_hand_quantity = 10, reserved_quantity = 0, damaged_quantity = 0,
                       version = 0, updated_at = CURRENT_TIMESTAMP
                  FROM warehouses w, skus s
                 WHERE ip.warehouse_id = w.id AND ip.sku_id = s.id
                   AND w.code = ? AND s.sku_code = ?
                """, WAREHOUSE, SKU);
    }

    @Test
    @Timeout(30)
    void neverReservesMoreThanAvailableStockUnderConcurrency() throws Exception {
        List<Future<InventoryMovementResponse>> results = runConcurrently(20, index ->
                request("it-capacity-" + index + "-" + UUID.randomUUID()));

        int successes = 0;
        int conflicts = 0;
        for (Future<InventoryMovementResponse> result : results) {
            try {
                result.get();
                successes++;
            } catch (ExecutionException error) {
                assertInstanceOf(InventoryConflictException.class, error.getCause());
                conflicts++;
            }
        }

        assertEquals(10, successes);
        assertEquals(10, conflicts);
        assertEquals(10, reservedQuantity());
        assertEquals(10, movementCount("it-capacity-%"));
    }

    @Test
    @Timeout(30)
    void appliesSimultaneousDuplicateRequestsOnlyOnce() throws Exception {
        String idempotencyKey = "it-duplicate-" + UUID.randomUUID();
        List<Future<InventoryMovementResponse>> results = runConcurrently(12, index -> request(idempotencyKey));

        int firstExecutions = 0;
        int idempotentReplays = 0;
        for (Future<InventoryMovementResponse> result : results) {
            if (result.get().idempotent()) {
                idempotentReplays++;
            } else {
                firstExecutions++;
            }
        }

        assertEquals(1, firstExecutions);
        assertEquals(11, idempotentReplays);
        assertEquals(1, reservedQuantity());
        assertEquals(1, movementCount(idempotencyKey));
    }

    private List<Future<InventoryMovementResponse>> runConcurrently(
            int workerCount, java.util.function.IntFunction<InventoryMovementRequest> requestFactory) {
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<InventoryMovementResponse>> futures = new ArrayList<>();
        for (int index = 0; index < workerCount; index++) {
            int taskIndex = index;
            futures.add(executor.submit(() -> {
                start.await();
                return inventoryService.move(requestFactory.apply(taskIndex), "integration-test");
            }));
        }
        start.countDown();
        executor.shutdown();
        return futures;
    }

    private InventoryMovementRequest request(String idempotencyKey) {
        return new InventoryMovementRequest(WAREHOUSE, SKU, InventoryMovementType.RESERVE,
                1, idempotencyKey, "동시성 통합 테스트");
    }

    private int reservedQuantity() {
        return jdbcTemplate.queryForObject("""
                SELECT ip.reserved_quantity
                  FROM inventory_positions ip
                  JOIN warehouses w ON w.id = ip.warehouse_id
                  JOIN skus s ON s.id = ip.sku_id
                 WHERE w.code = ? AND s.sku_code = ?
                """, Integer.class, WAREHOUSE, SKU);
    }

    private int movementCount(String idempotencyKeyPattern) {
        if (idempotencyKeyPattern.endsWith("%")) {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inventory_movements WHERE idempotency_key LIKE ?",
                    Integer.class, idempotencyKeyPattern);
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE idempotency_key = ?",
                Integer.class, idempotencyKeyPattern);
    }
}
