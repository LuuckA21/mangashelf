CREATE TABLE purchase_lists (
    id                    uuid        PRIMARY KEY,
    user_id               uuid        NOT NULL,
    description           text        NOT NULL,
    month                 int         NOT NULL,
    year                  int         NOT NULL,
    closed                boolean     NOT NULL DEFAULT false,
    discount_cents        int,
    points_discount_cents int         NOT NULL DEFAULT 0,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    CONSTRAINT fk_purchase_lists_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_purchase_lists_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_purchase_lists_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_purchase_lists_discount_cents CHECK (discount_cents >= 0),
    CONSTRAINT ck_purchase_lists_points_discount_cents CHECK (points_discount_cents >= 0)
);

CREATE INDEX ix_purchase_lists_user_id ON purchase_lists (user_id);

CREATE TABLE purchase_items (
    id              uuid        PRIMARY KEY,
    list_id         uuid        NOT NULL,
    edition_id      uuid        NOT NULL,
    volume          int         NOT NULL,
    release_date    date,
    price_eur_cents int         NOT NULL,
    price_chf_cents int         NOT NULL,
    booked          boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_purchase_items_list_id FOREIGN KEY (list_id) REFERENCES purchase_lists (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_items_edition_id FOREIGN KEY (edition_id) REFERENCES editions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_purchase_items_volume CHECK (volume >= 1),
    CONSTRAINT ck_purchase_items_price_eur_cents CHECK (price_eur_cents >= 0),
    CONSTRAINT ck_purchase_items_price_chf_cents CHECK (price_chf_cents >= 0)
);

CREATE INDEX ix_purchase_items_list_id ON purchase_items (list_id);
CREATE INDEX ix_purchase_items_edition_id ON purchase_items (edition_id);
