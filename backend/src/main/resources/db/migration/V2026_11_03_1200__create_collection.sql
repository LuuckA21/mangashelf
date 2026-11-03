CREATE TABLE collection_editions (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL,
    edition_id uuid        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_collection_editions_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_editions_edition_id FOREIGN KEY (edition_id) REFERENCES editions (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_collection_editions_user_id_edition_id ON collection_editions (user_id, edition_id);
CREATE INDEX ix_collection_editions_edition_id ON collection_editions (edition_id);

CREATE TABLE owned_volumes (
    id                    uuid        PRIMARY KEY,
    collection_edition_id uuid        NOT NULL,
    number                int         NOT NULL,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    CONSTRAINT fk_owned_volumes_collection_edition_id FOREIGN KEY (collection_edition_id) REFERENCES collection_editions (id) ON DELETE CASCADE,
    CONSTRAINT ck_owned_volumes_number CHECK (number >= 1)
);

CREATE UNIQUE INDEX uq_owned_volumes_collection_edition_id_number ON owned_volumes (collection_edition_id, number);
