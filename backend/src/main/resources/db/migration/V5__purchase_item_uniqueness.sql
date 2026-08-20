-- ============================================================
-- A volume can occur only once in one purchase list.
--
-- Older versions allowed duplicates. Consolidate them before adding the
-- constraint so an upgrade preserves the useful facts rather than failing
-- at startup or arbitrarily discarding the whole later row.
-- ============================================================

WITH merged AS (
    SELECT
        MIN(id) AS keep_id,
        list_id,
        series_id,
        volume_number,
        (array_agg(release_date ORDER BY id DESC)
            FILTER (WHERE release_date IS NOT NULL))[1] AS release_date,
        (array_agg(price_eur_cents ORDER BY id DESC)
            FILTER (WHERE price_eur_cents IS NOT NULL))[1] AS price_eur_cents,
        (array_agg(price_chf_cents ORDER BY id DESC)
            FILTER (WHERE price_chf_cents IS NOT NULL))[1] AS price_chf_cents,
        BOOL_OR(reserved) AS reserved,
        MIN(purchased_at) AS purchased_at,
        MIN(added_at) AS added_at
    FROM purchase_item
    GROUP BY list_id, series_id, volume_number
    HAVING COUNT(*) > 1
)
UPDATE purchase_item item
SET release_date = merged.release_date,
    price_eur_cents = merged.price_eur_cents,
    price_chf_cents = merged.price_chf_cents,
    reserved = merged.reserved,
    purchased_at = merged.purchased_at,
    added_at = merged.added_at
FROM merged
WHERE item.id = merged.keep_id;

DELETE FROM purchase_item item
USING (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY list_id, series_id, volume_number
               ORDER BY id
           ) AS occurrence
    FROM purchase_item
) duplicate
WHERE item.id = duplicate.id
  AND duplicate.occurrence > 1;

ALTER TABLE purchase_item
    ADD CONSTRAINT uq_purchase_item_list_series_volume
    UNIQUE (list_id, series_id, volume_number);
