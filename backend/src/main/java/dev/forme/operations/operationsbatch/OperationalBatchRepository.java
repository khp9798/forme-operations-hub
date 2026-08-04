package dev.forme.operations.operationsbatch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationalBatchRepository {
    private final JdbcTemplate jdbc;

    public OperationalBatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<BatchJobResponse> findJobs() {
        return jdbc.query("""
                SELECT j.job_code, j.name, j.description, j.cron_expression, j.enabled, j.max_retry_count,
                       latest.status, latest.started_at, latest.completed_at
                  FROM operational_batch_jobs j
                  LEFT JOIN LATERAL (
                      SELECT status, started_at, completed_at FROM operational_batch_executions e
                       WHERE e.job_code=j.job_code ORDER BY e.started_at DESC LIMIT 1
                  ) latest ON TRUE ORDER BY j.job_code
                """, (rs, row) -> new BatchJobResponse(rs.getString("job_code"), rs.getString("name"),
                rs.getString("description"), rs.getString("cron_expression"), rs.getBoolean("enabled"),
                rs.getInt("max_retry_count"), nullableStatus(rs.getString("status")),
                instant(rs, "started_at"), instant(rs, "completed_at")));
    }

    List<BatchExecutionResponse> findExecutions(BatchExecutionStatus status, int limit) {
        String condition = status == null ? "" : " WHERE e.status=?";
        Object[] args = status == null ? new Object[] { limit } : new Object[] { status.name(), limit };
        return jdbc.query("SELECT e.*, j.name AS job_name FROM operational_batch_executions e " +
                "JOIN operational_batch_jobs j ON j.job_code=e.job_code" + condition +
                " ORDER BY e.started_at DESC LIMIT ?", this::mapExecution, args);
    }

    JobDefinition findJob(String code) {
        List<JobDefinition> rows = jdbc.query("""
                SELECT job_code, handler_code, enabled, max_retry_count FROM operational_batch_jobs WHERE job_code=?
                """, (rs, row) -> new JobDefinition(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4)), code);
        if (rows.isEmpty()) throw new OperationalBatchNotFoundException("배치 작업을 찾을 수 없습니다.");
        return rows.getFirst();
    }

    PreviousExecution findPrevious(UUID id) {
        List<PreviousExecution> rows = jdbc.query("""
                SELECT id, job_code, status, attempt_number, retry_of FROM operational_batch_executions WHERE id=?
                """, (rs, row) -> new PreviousExecution(rs.getObject(1, UUID.class), rs.getString(2),
                BatchExecutionStatus.valueOf(rs.getString(3)), rs.getInt(4), rs.getObject(5, UUID.class)), id);
        if (rows.isEmpty()) throw new OperationalBatchNotFoundException("배치 실행 이력을 찾을 수 없습니다.");
        return rows.getFirst();
    }

    void insertRunning(UUID id, JobDefinition job, BatchTriggerType trigger, int attempt, UUID retryOf, String actor) {
        jdbc.update("""
                INSERT INTO operational_batch_executions
                    (id, job_code, trigger_type, status, attempt_number, retry_of, requested_by)
                VALUES (?, ?, ?, 'RUNNING', ?, ?, ?)
                """, id, job.jobCode(), trigger.name(), attempt, retryOf, actor);
    }

    void complete(UUID id, long count, String summary) {
        jdbc.update("""
                UPDATE operational_batch_executions SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP,
                       processed_count=?, result_summary=? WHERE id=?
                """, count, summary, id);
    }

    void fail(UUID id, String message) {
        jdbc.update("""
                UPDATE operational_batch_executions SET status='FAILED', completed_at=CURRENT_TIMESTAMP,
                       error_message=? WHERE id=?
                """, message, id);
    }

    BatchExecutionResponse findExecution(UUID id) {
        return jdbc.queryForObject("""
                SELECT e.*, j.name AS job_name FROM operational_batch_executions e
                  JOIN operational_batch_jobs j ON j.job_code=e.job_code WHERE e.id=?
                """, this::mapExecution, id);
    }

    private BatchExecutionResponse mapExecution(ResultSet rs, int row) throws SQLException {
        return new BatchExecutionResponse(rs.getObject("id", UUID.class), rs.getString("job_code"),
                rs.getString("job_name"), BatchTriggerType.valueOf(rs.getString("trigger_type")),
                BatchExecutionStatus.valueOf(rs.getString("status")), rs.getInt("attempt_number"),
                rs.getObject("retry_of", UUID.class), rs.getString("requested_by"),
                instant(rs, "started_at"), instant(rs, "completed_at"), rs.getInt("processed_count"),
                rs.getString("result_summary"), rs.getString("error_message"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static BatchExecutionStatus nullableStatus(String value) {
        return value == null ? null : BatchExecutionStatus.valueOf(value);
    }

    record JobDefinition(String jobCode, String handlerCode, boolean enabled, int maxRetryCount) { }
    record PreviousExecution(UUID id, String jobCode, BatchExecutionStatus status, int attemptNumber, UUID retryOf) { }
}
