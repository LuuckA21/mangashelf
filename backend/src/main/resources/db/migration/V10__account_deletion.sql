ALTER TABLE app_user
    ADD COLUMN deletion_hash VARCHAR(64),
    ADD COLUMN deletion_expires_at TIMESTAMPTZ,
    ADD COLUMN deletion_session_version INTEGER,
    ADD COLUMN deletion_requested_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_user_deletion_hash ON app_user (deletion_hash)
    WHERE deletion_hash IS NOT NULL;
