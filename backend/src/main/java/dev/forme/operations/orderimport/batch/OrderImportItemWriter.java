package dev.forme.operations.orderimport.batch;

import java.util.UUID;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

public class OrderImportItemWriter implements ItemWriter<StagedOrderRow> {
    private final JdbcTemplate jdbcTemplate;

    public OrderImportItemWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends StagedOrderRow> chunk) {
        for (StagedOrderRow row : chunk) {
            UUID orderId = jdbcTemplate.queryForObject("""
                    INSERT INTO external_orders
                        (id, source_system, source_order_id, ordered_at, currency, recipient_name,
                         postal_code, address_line1, address_line2, import_job_id)
                    VALUES (?, 'CSV_UPLOAD', ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source_system, source_order_id) DO UPDATE
                       SET updated_at = CURRENT_TIMESTAMP
                    RETURNING id
                    """, UUID.class, UUID.randomUUID(), row.sourceOrderId(), row.orderedAt(), row.currency(),
                    row.recipientName(), row.postalCode(), row.addressLine1(), row.addressLine2(), row.importJobId());

            jdbcTemplate.update("""
                    INSERT INTO external_order_items (id, order_id, sku_id, quantity, unit_price)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (order_id, sku_id) DO NOTHING
                    """, UUID.randomUUID(), orderId, row.skuId(), row.quantity(), row.unitPrice());
            jdbcTemplate.update("""
                    UPDATE order_import_rows
                       SET processing_status = 'PROCESSED', processing_error = NULL,
                           processed_at = CURRENT_TIMESTAMP
                     WHERE id = ?
                    """, row.rowId());
        }
    }
}
