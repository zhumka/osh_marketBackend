ALTER TABLE rent_contracts
    ADD COLUMN penalty_debt DECIMAL(12, 2) NOT NULL DEFAULT 0;
