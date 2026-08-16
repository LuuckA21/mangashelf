-- ============================================================
-- Purchase lists: what to buy this month, before it is owned.
--
-- Deliberately not tied to the catalogue's volume rows. A list is written
-- ahead of a release, when the volume does not exist yet — requiring it to
-- be catalogued first would put the planning behind the bookkeeping. The
-- edition is referenced, the volume number is just a number.
-- ============================================================

CREATE TABLE purchase_list (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,

    -- Two discount forms because shops give both: a percentage off the
    -- whole order, or a flat amount. Only one is meant to be set at a time;
    -- the check keeps a list from silently applying both.
    discount_percent NUMERIC(5,2) CHECK (discount_percent >= 0 AND discount_percent <= 100),
    discount_cents   INTEGER CHECK (discount_cents >= 0),
    CONSTRAINT one_discount_form CHECK (
        discount_percent IS NULL OR discount_cents IS NULL),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_purchase_list_user ON purchase_list (user_id);

CREATE TABLE purchase_item (
    id              BIGSERIAL PRIMARY KEY,
    list_id         BIGINT NOT NULL REFERENCES purchase_list(id) ON DELETE CASCADE,
    series_id       BIGINT NOT NULL REFERENCES series(id) ON DELETE CASCADE,

    volume_number   SMALLINT NOT NULL,
    release_date    DATE,

    -- Prices in cents of their own currency, never as decimals: a running
    -- total over twenty rows is exactly where floating point drifts.
    price_eur_cents INTEGER CHECK (price_eur_cents >= 0),
    price_chf_cents INTEGER CHECK (price_chf_cents >= 0),

    added_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_purchase_item_list ON purchase_item (list_id);
