-- Lets the bounded orphan sweep select only old asset IDs in creation order;
-- it never needs to read the bytea content column.
create index media_assets_created_at_id_idx on media_assets (created_at, id);
