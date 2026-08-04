package dev.forme.operations.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {
    private final JdbcTemplate jdbc;

    public AuditLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<AuditLogResponse> search(String keyword, String action) {
        return jdbc.query("""
                SELECT id, actor, action, entity_type, entity_id, summary, occurred_at
                  FROM audit_logs
                 WHERE (?='' OR actor ILIKE '%' || ? || '%' OR summary ILIKE '%' || ? || '%'
                               OR entity_id ILIKE '%' || ? || '%')
                   AND (?='' OR action=?)
                 ORDER BY occurred_at DESC LIMIT 300
                """, (rs, row) -> new AuditLogResponse(rs.getObject("id", UUID.class), rs.getString("actor"),
                rs.getString("action"), rs.getString("entity_type"), rs.getString("entity_id"),
                rs.getString("summary"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant()),
                keyword, keyword, keyword, keyword, action, action);
    }
}
