-- ============================================================
-- Per-line purchase state.
--
-- A list is written ahead of the month, and not everything on it gets
-- bought: some volumes slip to the next month. Marking the line rather
-- than the list is what lets the ones left behind be carried over.
-- ============================================================

ALTER TABLE purchase_item
    -- A nullable timestamp rather than a flag, for the same reason paid_at
    -- is one: "bought" and "bought when" are a single fact, and two columns
    -- would allow a line that is bought on no date.
    ADD COLUMN purchased_at TIMESTAMPTZ;
