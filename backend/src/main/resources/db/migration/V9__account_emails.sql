-- Existing accounts remain usable when email delivery is enabled.
ALTER TABLE app_user
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN verification_hash VARCHAR(64),
    ADD COLUMN verification_expires_at TIMESTAMPTZ,
    ADD COLUMN reset_hash VARCHAR(64),
    ADD COLUMN reset_expires_at TIMESTAMPTZ,
    ADD COLUMN reset_session_version INTEGER;
CREATE UNIQUE INDEX uq_user_verification_hash ON app_user (verification_hash)
    WHERE verification_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_user_reset_hash ON app_user (reset_hash)
    WHERE reset_hash IS NOT NULL;
