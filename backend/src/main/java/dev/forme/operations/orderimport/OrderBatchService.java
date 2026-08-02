package dev.forme.operations.orderimport;

import java.util.UUID;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderBatchService {
    private final JobOperator jobOperator;
    private final Job orderImportJob;
    private final JdbcTemplate jdbcTemplate;

    public OrderBatchService(JobOperator jobOperator,
                             @Qualifier("orderImportJob") Job orderImportJob,
                             JdbcTemplate jdbcTemplate) {
        this.jobOperator = jobOperator;
        this.orderImportJob = orderImportJob;
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrderBatchResponse process(UUID importJobId, String actor) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_jobs WHERE id = ? AND job_type = 'ORDER_VALIDATION'",
                Integer.class, importJobId);
        if (exists == null || exists == 0) throw new OrderImportValidationException("처리할 주문 업로드 작업을 찾지 못했습니다.");

        jdbcTemplate.update("UPDATE integration_jobs SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP, requested_by = ? WHERE id = ?",
                actor, importJobId);
        try {
            JobExecution execution = jobOperator.start(orderImportJob, new JobParametersBuilder()
                    .addString("importJobId", importJobId.toString())
                    .addString("attemptId", UUID.randomUUID().toString())
                    .toJobParameters());
            StepExecution step = execution.getStepExecutions().stream().findFirst().orElse(null);
            int processed = count(importJobId, "PROCESSED");
            int remaining = countPendingValid(importJobId);
            int invalid = countInvalid(importJobId);
            String integrationStatus = execution.getStatus().isUnsuccessful() ? "FAILED"
                    : invalid > 0 || remaining > 0 ? "PARTIAL_FAILED" : "COMPLETED";
            jdbcTemplate.update("""
                    UPDATE integration_jobs
                       SET status = ?, success_count = ?, failure_count = ?, completed_at = CURRENT_TIMESTAMP
                     WHERE id = ?
                    """, integrationStatus, processed, invalid + remaining, importJobId);
            return new OrderBatchResponse(importJobId, execution.getId(), execution.getStatus().name(),
                    step == null ? 0 : step.getReadCount(), step == null ? 0 : step.getWriteCount(),
                    processed, remaining, invalid);
        } catch (Exception exception) {
            jdbcTemplate.update("UPDATE integration_jobs SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP WHERE id = ?", importJobId);
            throw new IllegalStateException("주문 배치를 실행하지 못했습니다.", exception);
        }
    }

    private int count(UUID jobId, String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_import_rows WHERE integration_job_id = ? AND processing_status = ?",
                Integer.class, jobId, status);
    }

    private int countPendingValid(UUID jobId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM order_import_rows
                 WHERE integration_job_id = ? AND validation_status = 'VALID' AND processing_status <> 'PROCESSED'
                """, Integer.class, jobId);
    }

    private int countInvalid(UUID jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_import_rows WHERE integration_job_id = ? AND validation_status = 'INVALID'",
                Integer.class, jobId);
    }
}
