-- Remove columns that no code reads.
DROP INDEX IF EXISTS idx_dataset_owner;
DROP INDEX IF EXISTS idx_dataset_external_id;
ALTER TABLE datasets DROP COLUMN IF EXISTS owner_user_id;
ALTER TABLE datasets DROP COLUMN IF EXISTS external_id;
ALTER TABLE datasets DROP COLUMN IF EXISTS format;
ALTER TABLE datasets DROP COLUMN IF EXISTS manifest_urn;

ALTER TABLE pipeline_runtime_status DROP COLUMN IF EXISTS correlation_id;
