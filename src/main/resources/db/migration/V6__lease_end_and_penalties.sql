ALTER TABLE rent_contracts
    ADD COLUMN planned_end_date DATE,
    ADD COLUMN last_penalty_due_date DATE;

ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'PAYMENT_PENALTY';
