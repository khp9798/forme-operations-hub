CREATE SCHEMA analytics;

CREATE TABLE analytics.pipeline_runs (
    id UUID PRIMARY KEY,
    dag_id VARCHAR(120) NOT NULL,
    dag_run_id VARCHAR(250) NOT NULL,
    data_interval_start TIMESTAMPTZ NOT NULL,
    data_interval_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    extracted_count INTEGER NOT NULL DEFAULT 0 CHECK (extracted_count >= 0),
    rejected_count INTEGER NOT NULL DEFAULT 0 CHECK (rejected_count >= 0),
    loaded_count INTEGER NOT NULL DEFAULT 0 CHECK (loaded_count >= 0),
    error_message VARCHAR(1000),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT pipeline_runs_dag_run_unique UNIQUE (dag_id, dag_run_id),
    CONSTRAINT pipeline_runs_interval_valid CHECK (data_interval_end > data_interval_start)
);

CREATE TABLE analytics.stg_order_items (
    id UUID PRIMARY KEY,
    pipeline_run_id UUID NOT NULL REFERENCES analytics.pipeline_runs(id),
    source_order_item_id UUID NOT NULL,
    source_system VARCHAR(30) NOT NULL,
    source_order_id VARCHAR(100) NOT NULL,
    ordered_at TIMESTAMPTZ NOT NULL,
    sku_code VARCHAR(80) NOT NULL,
    brand_code VARCHAR(30) NOT NULL,
    style_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(14, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    validation_status VARCHAR(10) NOT NULL DEFAULT 'PENDING'
        CHECK (validation_status IN ('PENDING', 'VALID', 'INVALID')),
    validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_updated_at TIMESTAMPTZ NOT NULL,
    extracted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stg_order_items_source_item_unique UNIQUE (source_order_item_id)
);

CREATE TABLE analytics.rejected_order_items (
    id UUID PRIMARY KEY,
    pipeline_run_id UUID NOT NULL REFERENCES analytics.pipeline_runs(id),
    staging_row_id UUID NOT NULL REFERENCES analytics.stg_order_items(id),
    source_order_item_id UUID NOT NULL,
    error_codes JSONB NOT NULL,
    rejected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rejected_order_items_run_row_unique UNIQUE (pipeline_run_id, staging_row_id)
);

CREATE TABLE analytics.dim_products (
    product_key BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_product_id UUID NOT NULL UNIQUE,
    brand_code VARCHAR(30) NOT NULL,
    style_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    season_code VARCHAR(20),
    active BOOLEAN NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL,
    warehouse_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE analytics.fact_order_items (
    source_order_item_id UUID PRIMARY KEY,
    pipeline_run_id UUID NOT NULL REFERENCES analytics.pipeline_runs(id),
    product_key BIGINT NOT NULL REFERENCES analytics.dim_products(product_key),
    source_system VARCHAR(30) NOT NULL,
    source_order_id VARCHAR(100) NOT NULL,
    ordered_at TIMESTAMPTZ NOT NULL,
    order_date DATE NOT NULL,
    sku_code VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    gross_amount NUMERIC(18, 2) NOT NULL CHECK (gross_amount >= 0),
    currency CHAR(3) NOT NULL,
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fact_order_items_order_sku_unique UNIQUE (source_system, source_order_id, sku_code)
);

CREATE TABLE analytics.mart_daily_product_sales (
    sales_date DATE NOT NULL,
    product_key BIGINT NOT NULL REFERENCES analytics.dim_products(product_key),
    source_system VARCHAR(30) NOT NULL,
    currency CHAR(3) NOT NULL,
    order_count INTEGER NOT NULL CHECK (order_count >= 0),
    units_sold INTEGER NOT NULL CHECK (units_sold >= 0),
    gross_sales NUMERIC(18, 2) NOT NULL CHECK (gross_sales >= 0),
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (sales_date, product_key, source_system, currency)
);

CREATE INDEX pipeline_runs_started_idx
    ON analytics.pipeline_runs (started_at DESC);
CREATE INDEX pipeline_runs_status_idx
    ON analytics.pipeline_runs (status, started_at DESC);
CREATE INDEX stg_order_items_run_validation_idx
    ON analytics.stg_order_items (pipeline_run_id, validation_status, ordered_at);
CREATE INDEX fact_order_items_date_product_idx
    ON analytics.fact_order_items (order_date, product_key) INCLUDE (quantity, gross_amount);
CREATE INDEX mart_daily_product_sales_date_idx
    ON analytics.mart_daily_product_sales (sales_date DESC, product_key);

