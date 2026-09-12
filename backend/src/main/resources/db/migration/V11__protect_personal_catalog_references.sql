-- Service-level checks cannot protect against a shelf/list insert committed
-- between the check and the catalogue DELETE. Enforce this boundary in the DB.
-- Account and purchase-list deletion still cascade through their own FKs.
ALTER TABLE user_volume DROP CONSTRAINT user_volume_series_id_fkey;
ALTER TABLE user_volume ADD CONSTRAINT user_volume_series_id_fkey
    FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE RESTRICT;

ALTER TABLE purchase_item DROP CONSTRAINT purchase_item_series_id_fkey;
ALTER TABLE purchase_item ADD CONSTRAINT purchase_item_series_id_fkey
    FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE RESTRICT;
