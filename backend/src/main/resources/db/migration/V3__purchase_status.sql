-- ============================================================
-- Purchase lists: period, payment, and per-line reservation.
-- ============================================================

ALTER TABLE purchase_list
    -- Kept as two numbers rather than a date: a list covers a month, not a
    -- day, and a DATE would force an arbitrary day-of-month that then shows
    -- up in every query and every screen.
    ADD COLUMN period_year  SMALLINT CHECK (period_year BETWEEN 1900 AND 2200),
    ADD COLUMN period_month SMALLINT CHECK (period_month BETWEEN 1 AND 12),

    -- A nullable timestamp instead of a boolean: "paid" and "paid when" are
    -- the same fact, and splitting them into two columns allows the state
    -- where one says paid and the other has no date.
    ADD COLUMN paid_at      TIMESTAMPTZ;

-- Either both parts of the period or neither: a month without a year does
-- not identify anything.
ALTER TABLE purchase_list
    ADD CONSTRAINT period_complete CHECK (
        (period_year IS NULL) = (period_month IS NULL));

CREATE INDEX idx_purchase_list_period ON purchase_list (period_year, period_month);

ALTER TABLE purchase_item
    -- A plain flag, not a timestamp: this one is toggled on and off week by
    -- week as the shop confirms, and the moment it changed carries nothing.
    ADD COLUMN reserved BOOLEAN NOT NULL DEFAULT FALSE;
