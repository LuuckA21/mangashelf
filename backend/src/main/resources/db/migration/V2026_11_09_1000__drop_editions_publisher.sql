-- The free-text publisher was replaced by editions.publisher_id (V2026_11_06_1000) and is no longer
-- read or written since 0.2.0, so dropping it keeps 0.2.0 working against this schema (rollback).
ALTER TABLE editions DROP COLUMN publisher;
