package dev.forme.operations.operationsbatch;

import static org.junit.jupiter.api.Assertions.*;

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

@SpringBootTest(properties = "forme.scheduling.enabled=false")
@Testcontainers
class OperationalBatchIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("forme_ops_batch_test").withUsername("forme_test").withPassword("forme_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired OperationalBatchService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM operational_batch_executions");
        jdbc.update("DELETE FROM operational_batch_jobs WHERE job_code='ALWAYS_FAIL_TEST'");
    }

    @Test
    void recordsCompletedExecutionInASeparateLifecycle() {
        BatchExecutionResponse result = service.run("DAILY_SALES_AGGREGATE", BatchTriggerType.MANUAL, "admin");
        assertEquals(BatchExecutionStatus.COMPLETED, result.status());
        assertNotNull(result.completedAt());
        assertTrue(result.resultSummary().contains("판매 집계"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operational_batch_executions", Integer.class));
    }

    @Test
    void persistsFailuresAndCapsRetryLineage() {
        jdbc.update("""
                INSERT INTO operational_batch_jobs
                    (job_code, name, description, cron_expression, handler_code, enabled, max_retry_count)
                VALUES ('ALWAYS_FAIL_TEST', '실패 테스트', '재시도 검증', '0 0 0 * * *', 'UNKNOWN_HANDLER', TRUE, 2)
                """);
        BatchExecutionResponse first = service.run("ALWAYS_FAIL_TEST", BatchTriggerType.MANUAL, "admin");
        assertEquals(BatchExecutionStatus.FAILED, first.status());
        assertTrue(first.errorMessage().contains("등록되지 않은"));

        BatchExecutionResponse second = service.retry(first.id(), "admin");
        BatchExecutionResponse third = service.retry(second.id(), "admin");
        assertEquals(2, second.attemptNumber());
        assertEquals(first.id(), second.retryOf());
        assertEquals(3, third.attemptNumber());
        assertEquals(first.id(), third.retryOf());
        assertThrows(OperationalBatchConflictException.class, () -> service.retry(third.id(), "admin"));
    }

    @Test
    void databaseConstraintPreventsConcurrentExecutionOfSameJob() {
        jdbc.update("""
                INSERT INTO operational_batch_executions
                    (id, job_code, trigger_type, status, attempt_number, requested_by)
                VALUES (?, 'DAILY_SALES_AGGREGATE', 'SCHEDULED', 'RUNNING', 1, 'scheduler')
                """, UUID.randomUUID());
        OperationalBatchConflictException error = assertThrows(OperationalBatchConflictException.class,
                () -> service.run("DAILY_SALES_AGGREGATE", BatchTriggerType.MANUAL, "admin"));
        assertTrue(error.getMessage().contains("이미 실행 중"));
    }
}
