CREATE TABLE order_import_rows (
    id UUID PRIMARY KEY,
    integration_job_id UUID NOT NULL REFERENCES integration_jobs(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL CHECK (line_number >= 2),
    source_order_id VARCHAR(100),
    ordered_at TIMESTAMPTZ,
    sku_code VARCHAR(80),
    quantity INTEGER,
    unit_price NUMERIC(14, 2),
    currency CHAR(3),
    recipient_name VARCHAR(100),
    postal_code VARCHAR(20),
    address_line1 VARCHAR(300),
    address_line2 VARCHAR(300),
    validation_status VARCHAR(10) NOT NULL CHECK (validation_status IN ('VALID', 'INVALID')),
    error_codes VARCHAR(500),
    raw_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT order_import_rows_job_line_unique UNIQUE (integration_job_id, line_number)
);

CREATE INDEX order_import_rows_job_status_idx
    ON order_import_rows (integration_job_id, validation_status, line_number);
