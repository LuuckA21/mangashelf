CREATE TABLE admin_audit_event (
    id               BIGSERIAL PRIMARY KEY,
    actor_user_id    BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    actor_username   VARCHAR(32) NOT NULL,
    target_user_id   BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    target_username  VARCHAR(32) NOT NULL,
    action           VARCHAR(32) NOT NULL,
    old_value        VARCHAR(32) NOT NULL,
    new_value        VARCHAR(32) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_admin_audit_event_action CHECK (
        action IN ('ROLE_CHANGED', 'STATUS_CHANGED')
    )
);

CREATE INDEX ix_admin_audit_event_created_at
    ON admin_audit_event (created_at DESC, id DESC);
