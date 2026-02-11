ALTER TABLE assignments
    DROP COLUMN IF EXISTS is_inherited,
    DROP COLUMN IF EXISTS scope_id,
    DROP COLUMN IF EXISTS parent_assignment_id;

ALTER TABLE assignments
    ALTER COLUMN scope_type DROP NOT NULL;

ALTER TABLE assignments
    DROP CONSTRAINT IF EXISTS FK_ASSIGNMENTS_ON_PARENT_ASSIGNMENT;

ALTER TABLE assignments
    DROP CONSTRAINT IF EXISTS uk_assignment_group_role_scope;

ALTER TABLE assignments
    ADD COLUMN data_structure_id UUID,
    ADD COLUMN data_source_id UUID,
    ADD COLUMN dataset_id UUID,
    ADD COLUMN data_space_id UUID,
    ADD COLUMN catalog_id UUID;

ALTER TABLE assignments
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_DATASTRUCTURE FOREIGN KEY (data_structure_id) REFERENCES data_structures(id),
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_DATASOURCE FOREIGN KEY (data_source_id) REFERENCES data_sources(id),
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_DATASET FOREIGN KEY (dataset_id) REFERENCES datasets(id),
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_DATASPACE FOREIGN KEY (data_space_id) REFERENCES data_spaces(id),
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_CATALOG FOREIGN KEY (catalog_id) REFERENCES catalogs(id);

ALTER TABLE assignments
    ADD CONSTRAINT uk_assignment_group_role_scope UNIQUE NULLS NOT DISTINCT (group_id, role_id, scope_type, data_structure_id, data_source_id, dataset_id, data_space_id, catalog_id);

-- remove old idx_assignment_scope
DROP INDEX IF EXISTS idx_assignment_scope;

-- Indices for foreign key lookups
CREATE INDEX idx_assignment_datastructure ON assignments (data_structure_id);
CREATE INDEX idx_assignment_datasource ON assignments (data_source_id);
CREATE INDEX idx_assignment_dataset ON assignments (dataset_id);
CREATE INDEX idx_assignment_dataspace ON assignments (data_space_id);
CREATE INDEX idx_assignment_catalog ON assignments (catalog_id);

-- Composite index for scope-based queries
CREATE INDEX idx_assignment_scope ON assignments (scope_type, data_structure_id, data_source_id, dataset_id, data_space_id, catalog_id);