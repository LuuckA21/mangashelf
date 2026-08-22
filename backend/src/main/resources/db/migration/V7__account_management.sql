ALTER TABLE app_user
    ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_session_version CHECK (session_version >= 0);
