ALTER TABLE analytics.stg_order_items
    DROP CONSTRAINT stg_order_items_source_item_unique;

ALTER TABLE analytics.stg_order_items
    ADD CONSTRAINT stg_order_items_run_source_unique
        UNIQUE (pipeline_run_id, source_order_item_id);

ALTER TABLE analytics.rejected_order_items
    ADD COLUMN resolution_status VARCHAR(15) NOT NULL DEFAULT 'PENDING'
        CHECK (resolution_status IN ('PENDING', 'RESOLVED')),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN resolved_by_pipeline_run_id UUID REFERENCES analytics.pipeline_runs(id);

CREATE INDEX rejected_order_items_pending_idx
    ON analytics.rejected_order_items (rejected_at DESC)
    WHERE resolution_status = 'PENDING';

