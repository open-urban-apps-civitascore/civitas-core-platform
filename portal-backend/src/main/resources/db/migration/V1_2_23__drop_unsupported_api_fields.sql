-- Remove columns whose API fields had no effect.
ALTER TABLE data_structures DROP COLUMN IF EXISTS created_from_data_source;
ALTER TABLE data_structure_versions DROP COLUMN IF EXISTS data_structure_version_source;
ALTER TABLE datasets DROP COLUMN IF EXISTS identifier;
ALTER TABLE datasets DROP COLUMN IF EXISTS version;
ALTER TABLE layers DROP COLUMN IF EXISTS bbox_auto_calculate;

DROP INDEX IF EXISTS idx_user_active;
ALTER TABLE users DROP COLUMN IF EXISTS active;

DROP INDEX IF EXISTS idx_group_parent;
ALTER TABLE groups DROP COLUMN IF EXISTS parent_group_id;

DELETE FROM pipeline_runtime_status WHERE state = 'UNKNOWN';
