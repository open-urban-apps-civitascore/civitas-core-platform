-- Drop global name-uniqueness constraints on data entities to close a create-oracle
-- information disclosure: read access on these entities is scope-restricted, but create
-- access is global, so a uniqueness violation on insert leaked the existence of records
-- the caller had no permission to read. Avoiding human-readable name collisions is now
-- a management/data-architect responsibility; the canonical identifier remains `id`.
-- See gitlab issue #1453 for context.

ALTER TABLE datasets DROP CONSTRAINT IF EXISTS uk_dataset_name;
ALTER TABLE data_structures DROP CONSTRAINT IF EXISTS uk_data_structures_name;
ALTER TABLE data_sources DROP CONSTRAINT IF EXISTS uk_data_sources_name;
