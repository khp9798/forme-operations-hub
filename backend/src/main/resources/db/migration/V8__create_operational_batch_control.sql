CREATE TABLE operational_batch_jobs (
    job_code VARCHAR(80) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    cron_expression VARCHAR(80) NOT NULL,
    handler_code VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_retry_count INTEGER NOT NULL DEFAULT 3 CHECK (max_retry_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE operational_batch_executions (
    id UUID PRIMARY KEY,
    job_code VARCHAR(80) NOT NULL REFERENCES operational_batch_jobs(job_code),
    trigger_type VARCHAR(20) NOT NULL CHECK (trigger_type IN ('SCHEDULED', 'MANUAL', 'RETRY')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    attempt_number INTEGER NOT NULL DEFAULT 1 CHECK (attempt_number > 0),
    retry_of UUID REFERENCES operational_batch_executions(id),
    requested_by VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    processed_count INTEGER NOT NULL DEFAULT 0,
    result_summary VARCHAR(500),
    error_message VARCHAR(1000)
);

CREATE UNIQUE INDEX operational_batch_one_running_idx
    ON operational_batch_executions (job_code) WHERE status = 'RUNNING';
CREATE INDEX operational_batch_executions_started_idx
    ON operational_batch_executions (started_at DESC);
CREATE INDEX operational_batch_executions_status_idx
    ON operational_batch_executions (status, started_at DESC);

INSERT INTO operational_batch_jobs
    (job_code, name, description, cron_expression, handler_code, enabled, max_retry_count)
VALUES
    ('DAILY_SALES_AGGREGATE', '판매·재고 일별 집계',
     '최근 90일 주문을 날짜·SKU·채널별 분석 테이블로 다시 계산합니다.',
     '0 0 * * * *', 'SALES_AGGREGATE_90D', TRUE, 3);
