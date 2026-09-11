package dev.forme.operations.operationsbatch;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AirflowOperationsRepository {
    private final JdbcTemplate jdbc;

    public AirflowOperationsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<RejectedOrderItemResponse> findRejected(String status, int limit) {
        return jdbc.query("""
                SELECT rejected.id, staging.source_order_id, staging.sku_code, staging.product_name,
                       rejected.error_codes::text AS error_codes, rejected.resolution_status,
                       rejected.rejected_at, rejected.resolved_at
                  FROM analytics.rejected_order_items rejected
                  JOIN analytics.stg_order_items staging ON staging.id = rejected.staging_row_id
                 WHERE (? = '' OR rejected.resolution_status = ?)
                 ORDER BY rejected.rejected_at DESC
                 LIMIT ?
                """, (rs, row) -> new RejectedOrderItemResponse(
                rs.getObject("id", UUID.class), rs.getString("source_order_id"),
                rs.getString("sku_code"), rs.getString("product_name"), rs.getString("error_codes"),
                rs.getString("resolution_status"),
                rs.getObject("rejected_at", OffsetDateTime.class).toInstant(),
                rs.getObject("resolved_at", OffsetDateTime.class) == null ? null
                        : rs.getObject("resolved_at", OffsetDateTime.class).toInstant()), status, status, limit);
    }

    void writeTriggerAudit(String actor, AirflowDagRunResponse run) {
        jdbc.update("""
                INSERT INTO audit_logs (id, actor, action, entity_type, entity_id, summary)
                VALUES (?, ?, 'AIRFLOW_BACKFILL_TRIGGERED', 'AIRFLOW_DAG_RUN', ?, ?)
                """, UUID.randomUUID(), actor, run.dagRunId(),
                "분석 백필 %s ~ %s, 오류 재처리=%s".formatted(
                        run.intervalStart(), run.intervalEnd(), run.reprocessRejected()));
    }
}

