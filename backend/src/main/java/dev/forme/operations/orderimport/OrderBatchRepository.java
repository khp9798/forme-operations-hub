package dev.forme.operations.orderimport;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderBatchRepository {
    private final JdbcTemplate jdbc;

    public OrderBatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean validationJobExists(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM integration_jobs WHERE id = ? AND job_type = 'ORDER_VALIDATION'",
                Integer.class, id);
        return count != null && count > 0;
    }

    void markRunning(UUID id, String actor) {
        jdbc.update("UPDATE integration_jobs SET status='RUNNING', started_at=CURRENT_TIMESTAMP, requested_by=? WHERE id=?",
                actor, id);
    }

    void complete(UUID id, String status, int success, int failure) {
        jdbc.update("""
                UPDATE integration_jobs SET status=?, success_count=?, failure_count=?, completed_at=CURRENT_TIMESTAMP
                 WHERE id=?
                """, status, success, failure, id);
    }

    void fail(UUID id) {
        jdbc.update("UPDATE integration_jobs SET status='FAILED', completed_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    int countByProcessingStatus(UUID id, String status) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_import_rows WHERE integration_job_id=? AND processing_status=?",
                Integer.class, id, status);
    }

    int countPendingValid(UUID id) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_import_rows
                 WHERE integration_job_id=? AND validation_status='VALID' AND processing_status<>'PROCESSED'
                """, Integer.class, id);
    }

    int countInvalid(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_import_rows WHERE integration_job_id=? AND validation_status='INVALID'",
                Integer.class, id);
    }
}
