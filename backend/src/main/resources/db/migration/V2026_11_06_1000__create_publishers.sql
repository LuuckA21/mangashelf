CREATE TABLE publishers (
    id         uuid        NOT NULL,
    name       text        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_publishers PRIMARY KEY (id),
    CONSTRAINT ck_publishers_name CHECK (char_length(name) BETWEEN 1 AND 100)
);

CREATE UNIQUE INDEX uq_publishers_name ON publishers (lower(name));

-- editions.publisher (text) stays for rollback; a later release drops it.
ALTER TABLE editions ADD COLUMN publisher_id uuid;
ALTER TABLE editions ADD CONSTRAINT fk_editions_publisher_id
    FOREIGN KEY (publisher_id) REFERENCES publishers (id) ON DELETE RESTRICT;
CREATE INDEX ix_editions_publisher_id ON editions (publisher_id);
