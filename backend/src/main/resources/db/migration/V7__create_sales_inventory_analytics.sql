CREATE TABLE daily_sku_sales (
    sales_date DATE NOT NULL,
    sku_id UUID NOT NULL REFERENCES skus(id),
    source_system VARCHAR(30) NOT NULL,
    order_count INTEGER NOT NULL CHECK (order_count >= 0),
    units_sold INTEGER NOT NULL CHECK (units_sold >= 0),
    gross_sales NUMERIC(18, 2) NOT NULL CHECK (gross_sales >= 0),
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (sales_date, sku_id, source_system)
);

CREATE INDEX daily_sku_sales_date_sku_idx ON daily_sku_sales (sales_date DESC, sku_id);

CREATE TABLE daily_channel_sales (
    sales_date DATE NOT NULL,
    source_system VARCHAR(30) NOT NULL,
    order_count INTEGER NOT NULL CHECK (order_count >= 0),
    units_sold INTEGER NOT NULL CHECK (units_sold >= 0),
    gross_sales NUMERIC(18, 2) NOT NULL CHECK (gross_sales >= 0),
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (sales_date, source_system)
);

CREATE INDEX daily_channel_sales_date_idx ON daily_channel_sales (sales_date DESC);
CREATE INDEX external_orders_ordered_at_id_idx ON external_orders (ordered_at, id);
CREATE INDEX external_order_items_sku_order_cover_idx
    ON external_order_items (sku_id, order_id) INCLUDE (quantity, unit_price);
