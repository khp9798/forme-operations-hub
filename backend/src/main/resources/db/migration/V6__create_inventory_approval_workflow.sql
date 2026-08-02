CREATE TABLE inventory_adjustment_requests (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    sku_id UUID NOT NULL REFERENCES skus(id),
    movement_type VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by VARCHAR(100) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by VARCHAR(100),
    decision_comment VARCHAR(500),
    decided_at TIMESTAMPTZ,
    movement_id UUID REFERENCES inventory_movements(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor VARCHAR(100) NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(120) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX inventory_adjustment_requests_status_requested_idx
    ON inventory_adjustment_requests (status, requested_at DESC);
CREATE INDEX audit_logs_occurred_idx ON audit_logs (occurred_at DESC);
CREATE INDEX audit_logs_entity_idx ON audit_logs (entity_type, entity_id, occurred_at DESC);

-- 승인 분리 원칙을 직접 시험할 수 있는 로컬 포트폴리오 계정입니다.
-- 비밀번호는 ops-admin과 같은 forme-local-admin이며 BCrypt 해시만 저장합니다.
INSERT INTO app_users (id, username, password_hash, display_name) VALUES
    ('50000000-0000-0000-0000-000000000002', 'ops-approver',
     '$2y$12$EhC4Y57XztyiT/QPUN8lteV9I2Z3xAn4KUha.AXRwIhqBUkxUUXgS', '승인 담당자');

INSERT INTO user_roles (user_id, role_id, assigned_by) VALUES
    ('50000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000002', 'SYSTEM');
