CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(300)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(80) NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO roles (id, code, name, description) VALUES
    ('40000000-0000-0000-0000-000000000001', 'OPERATOR', '운영 담당자', '재고 조회와 이동 처리 권한'),
    ('40000000-0000-0000-0000-000000000002', 'APPROVER', '승인 담당자', '중요 업무 승인 권한'),
    ('40000000-0000-0000-0000-000000000003', 'ADMIN', '시스템 관리자', '사용자와 역할 관리 권한');

-- 로컬 포트폴리오 계정입니다. 비밀번호 forme-local-admin은 BCrypt 해시로만 저장합니다.
INSERT INTO app_users (id, username, password_hash, display_name) VALUES
    ('50000000-0000-0000-0000-000000000001', 'ops-admin',
     '$2y$12$EhC4Y57XztyiT/QPUN8lteV9I2Z3xAn4KUha.AXRwIhqBUkxUUXgS', '김형표');

INSERT INTO user_roles (user_id, role_id, assigned_by) VALUES
    ('50000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', 'SYSTEM'),
    ('50000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000003', 'SYSTEM');

CREATE INDEX user_roles_role_idx ON user_roles (role_id, user_id);
