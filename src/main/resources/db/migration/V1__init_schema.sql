CREATE TYPE user_role AS ENUM ('ADMIN', 'TENANT');
CREATE TYPE notification_type AS ENUM ('PAYMENT_REMINDER', 'PAYMENT_SUCCESS', 'SYSTEM');

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    inn VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(20),
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE tenants (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    full_name VARCHAR(255) NOT NULL,
    inn VARCHAR(20) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(255),
    passport_series VARCHAR(20),
    passport_number VARCHAR(20),
    passport_issued_date DATE,
    passport_issued_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE places (
    id BIGSERIAL PRIMARY KEY,
    place_number VARCHAR(20) NOT NULL UNIQUE,
    aisle VARCHAR(100),
    department VARCHAR(100),
    monthly_rent DECIMAL(12, 2) NOT NULL,
    is_occupied BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE rent_contracts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    place_id BIGINT NOT NULL REFERENCES places(id),
    start_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    debt DECIMAL(12, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL REFERENCES rent_contracts(id),
    amount DECIMAL(12, 2) NOT NULL,
    payment_date DATE NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    type notification_type NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE bank_links (
    id BIGSERIAL PRIMARY KEY,
    bank_name VARCHAR(100) NOT NULL,
    bank_code VARCHAR(50),
    url VARCHAR(500) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) NOT NULL,
    code VARCHAR(10),
    method VARCHAR(10),
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_inn ON users(inn);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_tenants_user_id ON tenants(user_id);
CREATE INDEX idx_tenants_inn ON tenants(inn);
CREATE INDEX idx_tenants_is_deleted ON tenants(is_deleted);
CREATE INDEX idx_places_is_occupied ON places(is_occupied);
CREATE INDEX idx_places_is_deleted ON places(is_deleted);
CREATE INDEX idx_rent_contracts_tenant_id ON rent_contracts(tenant_id);
CREATE INDEX idx_rent_contracts_place_id ON rent_contracts(place_id);
CREATE INDEX idx_rent_contracts_is_active ON rent_contracts(is_active);
CREATE INDEX idx_payments_contract_id ON payments(contract_id);
CREATE INDEX idx_payments_payment_date ON payments(payment_date);
CREATE INDEX idx_notifications_tenant_id ON notifications(tenant_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
