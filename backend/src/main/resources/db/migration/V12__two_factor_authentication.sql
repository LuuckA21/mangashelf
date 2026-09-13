ALTER TABLE app_user
    ADD COLUMN two_factor_secret VARCHAR(256),
    ADD COLUMN two_factor_last_step BIGINT NOT NULL DEFAULT -1,
    ADD COLUMN two_factor_pending_secret VARCHAR(256),
    ADD COLUMN two_factor_pending_expires_at TIMESTAMPTZ,
    ADD COLUMN two_factor_pending_version INTEGER;

CREATE TABLE two_factor_recovery_code (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, code_hash)
);
