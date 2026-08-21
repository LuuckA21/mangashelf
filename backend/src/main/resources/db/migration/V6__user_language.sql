ALTER TABLE app_user
    ADD COLUMN language VARCHAR(2) NOT NULL DEFAULT 'IT';

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_language CHECK (language IN ('IT', 'EN'));
