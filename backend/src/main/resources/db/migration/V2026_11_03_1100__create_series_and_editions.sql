CREATE TABLE series (
    id         uuid        PRIMARY KEY,
    title      text        NOT NULL,
    created_by uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_series_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_series_title ON series (lower(title));
CREATE INDEX ix_series_created_by ON series (created_by);

CREATE TABLE editions (
    id           uuid        PRIMARY KEY,
    series_id    uuid        NOT NULL,
    name         text        NOT NULL,
    publisher    text,
    type         text        NOT NULL,
    status       text        NOT NULL,
    volume_count int         NOT NULL,
    cover_file   text,
    created_by   uuid,
    version      bigint      NOT NULL,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT fk_editions_series_id FOREIGN KEY (series_id) REFERENCES series (id) ON DELETE RESTRICT,
    CONSTRAINT fk_editions_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_editions_type CHECK (type IN ('REGULAR', 'VARIANT', 'JAPANESE')),
    CONSTRAINT ck_editions_status CHECK (status IN ('ONGOING', 'COMPLETED')),
    CONSTRAINT ck_editions_volume_count CHECK (volume_count BETWEEN 1 AND 9999)
);

CREATE UNIQUE INDEX uq_editions_series_id_name ON editions (series_id, lower(name));
CREATE INDEX ix_editions_created_by ON editions (created_by);
