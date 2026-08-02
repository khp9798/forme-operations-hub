package dev.forme.operations.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final JdbcTemplate jdbcTemplate;

    public AuditLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AuditLogResponse> search(String query, String action) {
        String keyword = query == null ? "" : query.trim();
        String actionValue = action == null ? "" : action.trim();
        return jdbcTemplate.query("""
                SELECT id, actor, action, entity_type, entity_id, summary, occurred_at
                  FROM audit_logs
                 WHERE (? = '' OR actor ILIKE '%' || ? || '%' OR summary ILIKE '%' || ? || '%'
                                  OR entity_id ILIKE '%' || ? || '%')
                   AND (? = '' OR action = ?)
                 ORDER BY occurred_at DESC
                 LIMIT 300
                """, this::map, keyword, keyword, keyword, keyword, actionValue, actionValue);
    }

    private AuditLogResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogResponse(rs.getObject("id", UUID.class), rs.getString("actor"),
                rs.getString("action"), rs.getString("entity_type"), rs.getString("entity_id"),
                rs.getString("summary"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
    }
}
