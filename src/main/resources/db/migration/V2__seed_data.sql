-- Default bank links
INSERT INTO bank_links (bank_name, bank_code, url) VALUES
('Мбанк', 'mbank', 'https://mbank.kg'),
('Оптима Банк', 'optima', 'https://optimabank.kg'),
('Дос-Кредобанк', 'dos', 'https://doscredobank.kg'),
('Банк Азии', 'asia', 'https://bankasia.kg'),
('Баикал Банк', 'baikal', 'https://baikalbank.kg'),
('KICB', 'kicb', 'https://kicb.net');

-- Default admin account (password: Admin@123456)
-- Replace the hash below after first startup using the AdminInitializer bean
INSERT INTO users (inn, password_hash, role, email, phone)
VALUES ('0000000000', '$2a$12$PLACEHOLDER_REPLACE_ON_INIT', 'ADMIN', 'admin@oshmarket.kg', '+996700000000');
