-- Remove the DCAT entities that no API serves: catalogs, dataset series, distributions,
-- resources, agents and activities.
DELETE FROM assignments WHERE scope_type = 'CATALOG';
DROP INDEX IF EXISTS idx_assignment_catalog;
DROP INDEX IF EXISTS idx_assignment_scope;
ALTER TABLE assignments DROP CONSTRAINT IF EXISTS uk_assignment_group_role_scope;
ALTER TABLE assignments DROP COLUMN IF EXISTS catalog_id;
ALTER TABLE assignments ADD CONSTRAINT uk_assignment_group_role_scope UNIQUE NULLS NOT DISTINCT (group_id, role_id, scope_type, data_structure_id, data_source_id, dataset_id, datapool_id);
CREATE INDEX idx_assignment_scope ON assignments (scope_type, data_structure_id, data_source_id, dataset_id, datapool_id);

DROP TABLE IF EXISTS catalog_datasets;
DROP TABLE IF EXISTS catalog_children;
DROP TABLE IF EXISTS catalogs;

DROP TABLE IF EXISTS dataset_agents;
DROP TABLE IF EXISTS activity_agents;
DROP TABLE IF EXISTS distributions;
DROP TABLE IF EXISTS resources;
DROP TABLE IF EXISTS activities;
DROP TABLE IF EXISTS agents;

DROP INDEX IF EXISTS idx_dataset_series;
ALTER TABLE datasets DROP COLUMN IF EXISTS dataset_series_id;
DROP TABLE IF EXISTS dataset_series;
