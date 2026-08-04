package dev.forme.operations.orderimport;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderImportRepository {
    private final JdbcTemplate jdbc;

    public OrderImportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<String> findActiveSkuCodes() {
        return jdbc.queryForList("SELECT sku_code FROM skus WHERE active = TRUE", String.class);
    }

    void createValidationJob(UUID jobId, String fileName, String status, int total, int valid, int invalid, String actor) {
        jdbc.update("""
                INSERT INTO integration_jobs
                    (id, source_system, job_type, source_file_name, status, total_count,
                     success_count, failure_count, requested_by, started_at, completed_at)
                VALUES (?, 'CSV_UPLOAD', 'ORDER_VALIDATION', ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, jobId, fileName, status, total, valid, invalid, actor);
    }

    void insertRow(UUID jobId, int lineNumber, String sourceOrderId, OffsetDateTime orderedAt,
                   String skuCode, Integer quantity, BigDecimal unitPrice, String currency,
                   String recipientName, String postalCode, String address1, String address2,
                   String validationStatus, String errors, String rawJson) {
        jdbc.update("""
                INSERT INTO order_import_rows
                    (id, integration_job_id, line_number, source_order_id, ordered_at, sku_code,
                     quantity, unit_price, currency, recipient_name, postal_code, address_line1,
                     address_line2, validation_status, error_codes, raw_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), jobId, lineNumber, sourceOrderId, orderedAt, skuCode, quantity,
                unitPrice, currency, recipientName, postalCode, address1, address2,
                validationStatus, errors, rawJson);
    }
}
