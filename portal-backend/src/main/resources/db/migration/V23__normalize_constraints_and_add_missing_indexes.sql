-- Normalize constraint naming convention: uq_ -> uk_ to match project standard.
-- Add missing indexes on join tables defined in JPA @Index annotations.

-- Rename constraints: uq_ -> uk_
ALTER TABLE data_structures
    RENAME CONSTRAINT uq_data_structures_name TO uk_data_structures_name;

ALTER TABLE data_structure_versions
    RENAME CONSTRAINT uq_data_structure_versions_data_structure_version TO uk_data_structure_versions_data_structure_version;

-- Add missing indexes on join tables (defined in JPA but never created)
CREATE INDEX idx_activity_agents_activity ON activity_agents (activity_id);
CREATE INDEX idx_activity_agents_agent ON activity_agents (agent_id);

CREATE INDEX idx_catalog_children_parent ON catalog_children (parent_catalog_id);
CREATE INDEX idx_catalog_children_child ON catalog_children (child_catalog_id);

CREATE INDEX idx_catalog_datasets_catalog ON catalog_datasets (catalog_id);
CREATE INDEX idx_catalog_datasets_dataset ON catalog_datasets (dataset_id);

CREATE INDEX idx_dataset_agents_dataset ON dataset_agents (dataset_id);
CREATE INDEX idx_dataset_agents_agent ON dataset_agents (agent_id);

CREATE INDEX idx_dataset_dataspaces_dataset ON dataset_dataspaces (dataset_id);
CREATE INDEX idx_dataset_dataspaces_dataspace ON dataset_dataspaces (dataspace_id);

-- Add missing indexes on foreign key columns (not covered by existing indexes)
CREATE INDEX idx_group_parent ON groups (parent_group_id);
CREATE INDEX idx_dataspace_parent ON data_spaces (parent_dataspace_id);
CREATE INDEX idx_group_members_user ON group_members (user_id);
CREATE INDEX idx_role_permissions_role ON role_permissions (role_id);
