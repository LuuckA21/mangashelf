ALTER TABLE users
    ADD COLUMN chf_rate         numeric(4, 2) NOT NULL DEFAULT 1.20,
    ADD COLUMN discount_percent int           NOT NULL DEFAULT 10,
    ADD CONSTRAINT ck_users_chf_rate CHECK (chf_rate > 0),
    ADD CONSTRAINT ck_users_discount_percent CHECK (discount_percent BETWEEN 0 AND 100);
