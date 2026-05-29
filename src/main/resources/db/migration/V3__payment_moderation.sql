-- Способы оплаты (QR хранится в MinIO, в БД — только ключ объекта)
CREATE TABLE payment_methods (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    requisites VARCHAR(1000),
    qr_object_key VARCHAR(500),
    qr_content_type VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Расширение платежей под модерацию (онлайн-оплаты арендатора)
-- Существующие платежи внесены админом вручную => считаем подтверждёнными.
ALTER TABLE payments
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN payment_method_id BIGINT REFERENCES payment_methods(id),
    ADD COLUMN receipt_object_key VARCHAR(500),
    ADD COLUMN receipt_content_type VARCHAR(100),
    ADD COLUMN submitted_at TIMESTAMP,
    ADD COLUMN reviewed_at TIMESTAMP,
    ADD COLUMN reviewed_by BIGINT REFERENCES users(id),
    ADD COLUMN reject_reason VARCHAR(500);

CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payment_methods_is_active ON payment_methods(is_active);

-- Новые типы уведомлений (PostgreSQL 12+ допускает ADD VALUE внутри транзакции)
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'PAYMENT_PENDING';
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'PAYMENT_REJECTED';

-- Способ оплаты по умолчанию (QR загрузит администратор через админ-панель)
INSERT INTO payment_methods (name, requisites, sort_order) VALUES
('Оплата по QR', 'Отсканируйте QR-код в приложении банка и переведите точную сумму. Затем загрузите чек об оплате.', 1);
