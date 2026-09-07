package dev.forme.operations.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class AnalyticsPipelineSchemaIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("forme_ops_pipeline_test").withUsername("forme_test").withPassword("forme_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsSeparatedAnalyticsLayers() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.tables
                 WHERE table_schema = 'analytics'
                   AND table_name IN ('pipeline_runs', 'stg_order_items', 'rejected_order_items',
                                      'dim_products', 'fact_order_items', 'mart_daily_product_sales')
                """, Integer.class);

        assertEquals(6, tableCount);
    }

    @Test
    void preventsTheSameDagRunFromBeingRecordedTwice() {
        UUID runId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.parse("2026-09-06T00:00:00+09:00");
        OffsetDateTime end = start.plusDays(1);
        insertRun(runId, "scheduled__2026-09-06", start, end);

        assertThrows(DuplicateKeyException.class,
                () -> insertRun(UUID.randomUUID(), "scheduled__2026-09-06", start, end));
    }

    private void insertRun(UUID id, String dagRunId, OffsetDateTime start, OffsetDateTime end) {
        jdbcTemplate.update("""
                INSERT INTO analytics.pipeline_runs
                    (id, dag_id, dag_run_id, data_interval_start, data_interval_end, status)
                VALUES (?, 'forme_daily_sales_pipeline', ?, ?, ?, 'RUNNING')
                """, id, dagRunId, start, end);
    }
}
