CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('DISTRIBUTION_CENTER', 'STORE', 'VIRTUAL')),
    country_code CHAR(2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    brand_code VARCHAR(30) NOT NULL,
    style_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    season_code VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT products_brand_style_unique UNIQUE (brand_code, style_code)
);

CREATE TABLE skus (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    sku_code VARCHAR(80) NOT NULL UNIQUE,
    color_code VARCHAR(30) NOT NULL,
    size_code VARCHAR(30) NOT NULL,
    barcode VARCHAR(80) UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory_positions (
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    sku_id UUID NOT NULL REFERENCES skus(id),
    on_hand_quantity INTEGER NOT NULL DEFAULT 0 CHECK (on_hand_quantity >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    damaged_quantity INTEGER NOT NULL DEFAULT 0 CHECK (damaged_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (warehouse_id, sku_id),
    CONSTRAINT inventory_reservation_not_over_on_hand CHECK (reserved_quantity <= on_hand_quantity)
);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    sku_id UUID NOT NULL REFERENCES skus(id),
    movement_type VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity <> 0),
    reference_type VARCHAR(30),
    reference_id VARCHAR(100),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL
);

CREATE TABLE integration_jobs (
    id UUID PRIMARY KEY,
    source_system VARCHAR(30) NOT NULL,
    job_type VARCHAR(40) NOT NULL,
    source_file_name VARCHAR(255),
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIAL_FAILED', 'FAILED')),
    total_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    requested_by VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX inventory_movements_sku_occurred_idx ON inventory_movements (sku_id, occurred_at DESC);
CREATE INDEX inventory_movements_reference_idx ON inventory_movements (reference_type, reference_id);
CREATE INDEX integration_jobs_status_created_idx ON integration_jobs (status, created_at DESC);
