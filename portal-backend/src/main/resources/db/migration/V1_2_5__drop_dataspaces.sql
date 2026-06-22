-- Remove DataSpace tables and columns (superseded by DataPools, was preview-only).
DROP TABLE IF EXISTS dataset_dataspaces;
DROP INDEX IF EXISTS idx_assignment_dataspace;
DROP INDEX IF EXISTS idx_assignment_scope;
ALTER TABLE assignments DROP CONSTRAINT IF EXISTS uk_assignment_group_role_scope;
ALTER TABLE assignments DROP COLUMN IF EXISTS data_space_id;
ALTER TABLE assignments ADD CONSTRAINT uk_assignment_group_role_scope UNIQUE NULLS NOT DISTINCT (group_id, role_id, scope_type, data_structure_id, data_source_id, dataset_id, catalog_id, datapool_id);
CREATE INDEX idx_assignment_scope ON assignments (scope_type, data_structure_id, data_source_id, dataset_id, catalog_id, datapool_id);
DROP TABLE IF EXISTS data_spaces;
