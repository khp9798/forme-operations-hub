package dev.forme.operations.orderimport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class OrderImportIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("forme_ops_import_test").withUsername("forme_test").withPassword("forme_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired OrderImportService service;
    @Autowired OrderBatchService batchService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearImports() {
        jdbcTemplate.update("DELETE FROM external_order_items");
        jdbcTemplate.update("DELETE FROM external_orders");
        jdbcTemplate.update("DELETE FROM integration_jobs WHERE job_type = 'ORDER_VALIDATION'");
    }

    @Test
    void storesValidAndInvalidRowsWithReasons() {
        String csv = "\uFEFF" + """
                source_order_id,ordered_at,sku_code,quantity,unit_price,currency,recipient_name,postal_code,address_line1,address_line2
                EXT-1,2026-08-02T10:30:00+09:00,MLB-CAP-0091-BK-F,2,39000,KRW,김포르메,04524,서울 중구,테스트
                EXT-2,not-a-date,UNKNOWN-SKU,0,-1,KR,이오퍼레이터,,,
                """;

        OrderImportResponse result = service.validate(new MockMultipartFile(
                "file", "orders.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)), "integration-test");

        assertEquals("PARTIAL_FAILED", result.status());
        assertEquals(2, result.totalCount());
        assertEquals(1, result.validCount());
        assertEquals(1, result.invalidCount());
        assertEquals("VALID", result.rows().getFirst().status());
        assertEquals("INVALID", result.rows().getLast().status());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_import_rows WHERE integration_job_id = ?", Integer.class, result.jobId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_import_rows WHERE integration_job_id = ? AND validation_status = 'INVALID'",
                Integer.class, result.jobId()));

        OrderBatchResponse firstRun = batchService.process(result.jobId(), "integration-test");
        assertEquals("COMPLETED", firstRun.status());
        assertEquals(1, firstRun.readCount());
        assertEquals(1, firstRun.writeCount());
        assertEquals(1, firstRun.processedCount());
        assertEquals(0, firstRun.remainingCount());
        assertEquals(1, firstRun.invalidCount());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM external_orders", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM external_order_items", Integer.class));

        OrderBatchResponse rerun = batchService.process(result.jobId(), "integration-test");
        assertEquals(0, rerun.readCount());
        assertEquals(0, rerun.writeCount());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM external_orders", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM external_order_items", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM batch_job_execution", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM batch_step_execution", Integer.class));
    }
}
