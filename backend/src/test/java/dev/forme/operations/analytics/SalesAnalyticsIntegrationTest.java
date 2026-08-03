package dev.forme.operations.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

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
class SalesAnalyticsIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("forme_ops_analytics_test").withUsername("forme_test").withPassword("forme_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired SalesAnalyticsService service;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedOrders() {
        jdbcTemplate.update("DELETE FROM audit_logs WHERE action = 'SALES_AGGREGATE_REFRESHED'");
        jdbcTemplate.update("DELETE FROM daily_sku_sales");
        jdbcTemplate.update("DELETE FROM daily_channel_sales");
        jdbcTemplate.update("DELETE FROM external_order_items");
        jdbcTemplate.update("DELETE FROM external_orders");
        jdbcTemplate.update("DELETE FROM integration_jobs WHERE source_system = 'ANALYTICS_TEST'");

        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO integration_jobs
                    (id, source_system, job_type, status, total_count, success_count, requested_by)
                VALUES (?, 'ANALYTICS_TEST', 'ORDER_VALIDATION', 'COMPLETED', 3, 3, 'test')
                """, jobId);
        UUID firstOrder = insertOrder(jobId, "AN-1");
        UUID secondOrder = insertOrder(jobId, "AN-2");
        insertItem(firstOrder, "30000000-0000-0000-0000-000000000001", 2, "100.00");
        insertItem(firstOrder, "30000000-0000-0000-0000-000000000002", 1, "200.00");
        insertItem(secondOrder, "30000000-0000-0000-0000-000000000001", 3, "100.00");
    }

    @Test
    void refreshesDailyAggregatesWithoutDoubleCountingMultiSkuOrders() {
        assertEquals(0, service.dashboard(30).orderCount());

        AggregateRefreshResponse refreshed = service.refresh(30, "analytics-admin");
        SalesInventoryDashboardResponse dashboard = service.dashboard(30);

        assertEquals(2, refreshed.aggregateRows());
        assertEquals(2, dashboard.orderCount());
        assertEquals(6, dashboard.unitsSold());
        assertEquals(0, new BigDecimal("700.00").compareTo(dashboard.grossSales()));
        assertEquals(2, dashboard.items().size());
        SalesInventoryItemResponse mlb = dashboard.items().stream()
                .filter(item -> item.brandCode().equals("MLB")).findFirst().orElseThrow();
        assertEquals(2, mlb.orderCount());
        assertEquals(5, mlb.unitsSold());
        assertEquals(0, new BigDecimal("500.00").compareTo(mlb.grossSales()));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_sku_sales", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_channel_sales", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'SALES_AGGREGATE_REFRESHED'", Integer.class));

        service.refresh(30, "analytics-admin");
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_sku_sales", Integer.class));
        assertEquals(2, service.dashboard(30).orderCount());
    }

    @Test
    void exposesReadOnlyExplainAnalyzeResultsForBothStrategies() {
        service.refresh(30, "analytics-admin");
        QueryPlanResponse plans = service.comparePlans(30);
        assertFalse(plans.rawQuery().planLines().isEmpty());
        assertFalse(plans.aggregateQuery().planLines().isEmpty());
        assertTrue(plans.rawQuery().executionTimeMs() >= 0);
        assertTrue(plans.aggregateQuery().executionTimeMs() >= 0);
    }

    @Test
    void generatesIsolatedSamplesAndComparesIndexPlans() {
        BenchmarkSeedResponse generated = service.generateBenchmarkData(10_000, "analytics-admin");
        assertEquals(10_000, generated.sampleRows());
        assertEquals(1000, generated.distinctSkus());

        IndexBenchmarkResponse benchmark = service.compareIndexPlans(30);
        assertEquals(10_000, benchmark.sampleRows());
        assertFalse(benchmark.withoutIndex().planLines().isEmpty());
        assertFalse(benchmark.withIndex().planLines().isEmpty());
        assertTrue(benchmark.withoutIndex().executionTimeMs() >= 0);
        assertTrue(benchmark.withIndex().executionTimeMs() >= 0);
    }

    private UUID insertOrder(UUID jobId, String sourceOrderId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO external_orders
                    (id, source_system, source_order_id, ordered_at, currency, recipient_name,
                     postal_code, address_line1, import_job_id)
                VALUES (?, 'ANALYTICS_TEST', ?, CURRENT_TIMESTAMP - INTERVAL '1 day', 'KRW',
                        '테스트', '04524', '서울시 중구', ?)
                """, id, sourceOrderId, jobId);
        return id;
    }

    private void insertItem(UUID orderId, String skuId, int quantity, String unitPrice) {
        jdbcTemplate.update("""
                INSERT INTO external_order_items (id, order_id, sku_id, quantity, unit_price)
                VALUES (?, ?, ?::uuid, ?, ?::numeric)
                """, UUID.randomUUID(), orderId, skuId, quantity, unitPrice);
    }
}
