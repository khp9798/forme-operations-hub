package dev.forme.operations.operationsbatch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.forme.operations.analytics.AggregateRefreshResponse;
import dev.forme.operations.analytics.SalesAnalyticsService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OperationalBatchService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SalesAnalyticsService analytics;

    public OperationalBatchService(JdbcTemplate jdbc, TransactionTemplate transactions, SalesAnalyticsService analytics) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.analytics = analytics;
    }

    public List<BatchJobResponse> jobs() {
        return jdbc.query("""
                SELECT j.job_code, j.name, j.description, j.cron_expression, j.enabled, j.max_retry_count,
                       latest.status, latest.started_at, latest.completed_at
                  FROM operational_batch_jobs j
                  LEFT JOIN LATERAL (
                      SELECT status, started_at, completed_at FROM operational_batch_executions e
                       WHERE e.job_code = j.job_code ORDER BY e.started_at DESC LIMIT 1
                  ) latest ON TRUE ORDER BY j.job_code
                """, (rs, row) -> new BatchJobResponse(rs.getString("job_code"), rs.getString("name"),
                rs.getString("description"), rs.getString("cron_expression"), rs.getBoolean("enabled"),
                rs.getInt("max_retry_count"), nullableStatus(rs.getString("status")),
                instant(rs, "started_at"), instant(rs, "completed_at")));
    }

    public List<BatchExecutionResponse> executions(BatchExecutionStatus status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String condition = status == null ? "" : " WHERE e.status = ?";
        Object[] args = status == null ? new Object[] { safeLimit } : new Object[] { status.name(), safeLimit };
        return jdbc.query("SELECT e.*, j.name AS job_name FROM operational_batch_executions e " +
                "JOIN operational_batch_jobs j ON j.job_code=e.job_code" + condition +
                " ORDER BY e.started_at DESC LIMIT ?", this::mapExecution, args);
    }

    public BatchExecutionResponse run(String jobCode, BatchTriggerType trigger, String actor) {
        JobDefinition job = findJob(jobCode);
        if (!job.enabled()) throw new OperationalBatchConflictException("비활성화된 배치 작업입니다.");
        return execute(job, trigger, actor, 1, null);
    }

    public BatchExecutionResponse retry(UUID executionId, String actor) {
        PreviousExecution previous = findPrevious(executionId);
        if (previous.status() != BatchExecutionStatus.FAILED)
            throw new OperationalBatchConflictException("실패한 실행만 재시도할 수 있습니다.");
        JobDefinition job = findJob(previous.jobCode());
        int attempt = previous.attemptNumber() + 1;
        if (attempt > job.maxRetryCount() + 1)
            throw new OperationalBatchConflictException("최대 재시도 횟수를 초과했습니다.");
        UUID root = previous.retryOf() == null ? previous.id() : previous.retryOf();
        return execute(job, BatchTriggerType.RETRY, actor, attempt, root);
    }

    private BatchExecutionResponse execute(JobDefinition job, BatchTriggerType trigger, String actor,
                                             int attempt, UUID retryOf) {
        UUID id = UUID.randomUUID();
        try {
            transactions.executeWithoutResult(tx -> jdbc.update("""
                    INSERT INTO operational_batch_executions
                        (id, job_code, trigger_type, status, attempt_number, retry_of, requested_by)
                    VALUES (?, ?, ?, 'RUNNING', ?, ?, ?)
                    """, id, job.jobCode(), trigger.name(), attempt, retryOf, actor));
        } catch (DataIntegrityViolationException error) {
            throw new OperationalBatchConflictException("이미 실행 중인 배치 작업입니다.");
        }
        try {
            BatchResult result = invoke(job.handlerCode(), actor);
            transactions.executeWithoutResult(tx -> jdbc.update("""
                    UPDATE operational_batch_executions SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP,
                           processed_count=?, result_summary=? WHERE id=?
                    """, result.processedCount(), result.summary(), id));
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (message.length() > 1000) message = message.substring(0, 1000);
            String finalMessage = message;
            transactions.executeWithoutResult(tx -> jdbc.update("""
                    UPDATE operational_batch_executions SET status='FAILED', completed_at=CURRENT_TIMESTAMP,
                           error_message=? WHERE id=?
                    """, finalMessage, id));
        }
        return execution(id);
    }

    private BatchResult invoke(String handler, String actor) {
        if ("SALES_AGGREGATE_90D".equals(handler)) {
            AggregateRefreshResponse result = analytics.refresh(90, actor);
            return new BatchResult(result.aggregateRows(), "최근 90일 판매 집계 %d행 갱신".formatted(result.aggregateRows()));
        }
        throw new IllegalStateException("등록되지 않은 배치 핸들러입니다: " + handler);
    }

    private JobDefinition findJob(String code) {
        List<JobDefinition> rows = jdbc.query("SELECT job_code, handler_code, enabled, max_retry_count FROM operational_batch_jobs WHERE job_code=?",
                (rs, row) -> new JobDefinition(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4)), code);
        if (rows.isEmpty()) throw new OperationalBatchNotFoundException("배치 작업을 찾을 수 없습니다.");
        return rows.getFirst();
    }

    private PreviousExecution findPrevious(UUID id) {
        List<PreviousExecution> rows = jdbc.query("SELECT id, job_code, status, attempt_number, retry_of FROM operational_batch_executions WHERE id=?",
                (rs, row) -> new PreviousExecution(rs.getObject(1, UUID.class), rs.getString(2),
                        BatchExecutionStatus.valueOf(rs.getString(3)), rs.getInt(4), rs.getObject(5, UUID.class)), id);
        if (rows.isEmpty()) throw new OperationalBatchNotFoundException("배치 실행 이력을 찾을 수 없습니다.");
        return rows.getFirst();
    }

    private BatchExecutionResponse execution(UUID id) {
        return jdbc.queryForObject("SELECT e.*, j.name AS job_name FROM operational_batch_executions e JOIN operational_batch_jobs j ON j.job_code=e.job_code WHERE e.id=?",
                this::mapExecution, id);
    }

    private BatchExecutionResponse mapExecution(ResultSet rs, int row) throws SQLException {
        return new BatchExecutionResponse(rs.getObject("id", UUID.class), rs.getString("job_code"), rs.getString("job_name"),
                BatchTriggerType.valueOf(rs.getString("trigger_type")), BatchExecutionStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_number"), rs.getObject("retry_of", UUID.class), rs.getString("requested_by"),
                instant(rs, "started_at"), instant(rs, "completed_at"), rs.getInt("processed_count"),
                rs.getString("result_summary"), rs.getString("error_message"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
    private static BatchExecutionStatus nullableStatus(String value) { return value == null ? null : BatchExecutionStatus.valueOf(value); }
    private record JobDefinition(String jobCode, String handlerCode, boolean enabled, int maxRetryCount) { }
    private record PreviousExecution(UUID id, String jobCode, BatchExecutionStatus status, int attemptNumber, UUID retryOf) { }
    private record BatchResult(long processedCount, String summary) { }
}
