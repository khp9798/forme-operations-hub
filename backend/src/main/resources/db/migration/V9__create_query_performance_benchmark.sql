CREATE TABLE sales_query_benchmark_heap (
    id BIGINT PRIMARY KEY,
    ordered_at TIMESTAMPTZ NOT NULL,
    sku_code VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    gross_amount NUMERIC(18, 2) NOT NULL CHECK (gross_amount >= 0)
);

CREATE TABLE sales_query_benchmark_indexed (
    id BIGINT PRIMARY KEY,
    ordered_at TIMESTAMPTZ NOT NULL,
    sku_code VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    gross_amount NUMERIC(18, 2) NOT NULL CHECK (gross_amount >= 0)
);

CREATE INDEX sales_query_benchmark_covering_idx
    ON sales_query_benchmark_indexed (ordered_at, sku_code)
    INCLUDE (quantity, gross_amount);

