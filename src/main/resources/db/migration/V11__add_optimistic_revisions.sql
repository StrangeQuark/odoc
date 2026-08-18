-- Establish a database-level optimistic-concurrency primitive before account,
-- tenancy, and document APIs begin relying on revision-aware mutations.
ALTER TABLE spaces ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pages ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
