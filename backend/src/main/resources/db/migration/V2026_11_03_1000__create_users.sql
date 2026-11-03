CREATE TABLE users (
    id                   uuid        PRIMARY KEY,
    username             text        NOT NULL,
    password_hash        text        NOT NULL,
    role                 text        NOT NULL,
    must_change_password boolean     NOT NULL,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_users_username CHECK (username ~ '^[a-zA-Z0-9._-]{3,32}$')
);

CREATE UNIQUE INDEX uq_users_username ON users (lower(username));
