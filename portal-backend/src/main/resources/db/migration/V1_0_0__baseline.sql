-- ============================================================================
-- CIVITAS Core Platform — Database Baseline
-- ============================================================================
-- Consolidates migrations V1–V23 into a single baseline.
-- This file represents the complete schema as of v1.0.0.
--
-- For existing environments: drop flyway_schema_history before deploying.
-- Flyway's baseline-on-migrate will skip this file and record a baseline entry.
--
-- For fresh environments: Flyway runs this file to create the full schema.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Independent tables (no foreign keys to other application tables)
-- ----------------------------------------------------------------------------

CREATE TABLE users
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    first_name  VARCHAR(255)                NOT NULL,
    last_name   VARCHAR(255)                NOT NULL,
    email       VARCHAR(255)                NOT NULL,
    phone       VARCHAR(255),
    external_id VARCHAR(255),
    active      BOOLEAN                     NOT NULL,
    title       VARCHAR(255)                NOT NULL DEFAULT 'OTHER',
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_user_email UNIQUE (email)
);

CREATE INDEX idx_user_active ON users (active);
CREATE INDEX idx_user_email ON users (email);
CREATE INDEX idx_user_external_id ON users (external_id);

CREATE TABLE resources
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    CONSTRAINT pk_resources PRIMARY KEY (id)
);

CREATE TABLE roles
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    role_type   VARCHAR(255)                NOT NULL,
    readonly    BOOLEAN                     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_role_name UNIQUE (name)
);

CREATE INDEX idx_role_type ON roles (role_type);

CREATE TABLE permissions
(
    id              UUID                        NOT NULL,
    name            VARCHAR(255)                NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_by      UUID,
    modified_by     UUID,
    permission_type VARCHAR(255)                NOT NULL,
    category        VARCHAR(255)                NOT NULL DEFAULT 'SYSTEM',
    source          VARCHAR(255)                NOT NULL DEFAULT 'INTERNAL',
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uk_permission_name_source UNIQUE (name, source)
);

CREATE INDEX idx_permission_type ON permissions (permission_type);

CREATE TABLE activities
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    CONSTRAINT pk_activities PRIMARY KEY (id),
    CONSTRAINT uk_activity_name UNIQUE (name)
);

CREATE TABLE agents
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    name        VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_agents PRIMARY KEY (id)
);

CREATE TABLE catalogs
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    CONSTRAINT pk_catalogs PRIMARY KEY (id),
    CONSTRAINT uk_catalog_name UNIQUE (name)
);

CREATE TABLE dataset_series
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    CONSTRAINT pk_dataset_series PRIMARY KEY (id),
    CONSTRAINT uk_dataset_series_name UNIQUE (name)
);

CREATE TABLE data_structures
(
    id                       UUID                        NOT NULL,
    name                     VARCHAR(255)                NOT NULL,
    description              TEXT,
    created_at               TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at              TIMESTAMP WITHOUT TIME ZONE,
    created_by               UUID,
    modified_by              UUID,
    data_structure_status    VARCHAR(50)                 NOT NULL,
    created_from_data_source BOOLEAN                     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_data_structures PRIMARY KEY (id),
    CONSTRAINT uk_data_structures_name UNIQUE (name)
);

-- ----------------------------------------------------------------------------
-- Dependent tables (reference independent tables)
-- ----------------------------------------------------------------------------

CREATE TABLE groups
(
    id              UUID                        NOT NULL,
    name            VARCHAR(255)                NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_by      UUID,
    modified_by     UUID,
    contact_user_id UUID,
    parent_group_id UUID,
    CONSTRAINT pk_groups PRIMARY KEY (id),
    CONSTRAINT uk_group_name UNIQUE (name),
    CONSTRAINT FK_GROUPS_ON_CONTACT_USER FOREIGN KEY (contact_user_id) REFERENCES users (id),
    CONSTRAINT FK_GROUPS_ON_PARENT_GROUP FOREIGN KEY (parent_group_id) REFERENCES groups (id)
);

CREATE INDEX idx_group_contact ON groups (contact_user_id);
CREATE INDEX idx_group_parent ON groups (parent_group_id);

CREATE TABLE data_spaces
(
    id                  UUID                        NOT NULL,
    name                VARCHAR(255)                NOT NULL,
    description         TEXT,
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at         TIMESTAMP WITHOUT TIME ZONE,
    created_by          UUID,
    modified_by         UUID,
    owner_user_id       UUID,
    parent_dataspace_id UUID,
    external_id         VARCHAR(255),
    CONSTRAINT pk_data_spaces PRIMARY KEY (id),
    CONSTRAINT uk_dataspace_name UNIQUE (name),
    CONSTRAINT FK_DATA_SPACES_ON_OWNER_USER FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT FK_DATA_SPACES_ON_PARENT_DATASPACE FOREIGN KEY (parent_dataspace_id) REFERENCES data_spaces (id)
);

CREATE INDEX idx_dataspace_owner ON data_spaces (owner_user_id);
CREATE INDEX idx_dataspace_parent ON data_spaces (parent_dataspace_id);

CREATE TABLE datasets
(
    id                UUID                        NOT NULL,
    name              VARCHAR(255)                NOT NULL,
    description       TEXT,
    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at       TIMESTAMP WITHOUT TIME ZONE,
    created_by        UUID,
    modified_by       UUID,
    owner_user_id     UUID,
    external_id       VARCHAR(255),
    format            VARCHAR(255),
    dataset_series_id UUID,
    identifier        VARCHAR(255),
    version           VARCHAR(255),
    status            VARCHAR(20)                 NOT NULL DEFAULT 'DRAFT',
    persistence_id    BIGINT,
    open_data_access  BOOLEAN                     NOT NULL DEFAULT FALSE,
    project_id        VARCHAR(255),
    frost_base_url    VARCHAR(500),
    route_id          VARCHAR(255),
    service_id        VARCHAR(255),
    public_url        VARCHAR(500),
    pipeline_ids      TEXT[],
    pending_saga_type VARCHAR(30),
    CONSTRAINT pk_datasets PRIMARY KEY (id),
    CONSTRAINT uk_dataset_name UNIQUE (name),
    CONSTRAINT FK_DATASETS_ON_OWNER_USER FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT FK_DATASETS_ON_DATASET_SERIES FOREIGN KEY (dataset_series_id) REFERENCES dataset_series (id)
);

CREATE INDEX idx_dataset_external_id ON datasets (external_id);
CREATE INDEX idx_dataset_owner ON datasets (owner_user_id);
CREATE INDEX idx_dataset_series ON datasets (dataset_series_id);
CREATE INDEX idx_dataset_status ON datasets (status);
CREATE INDEX idx_dataset_persistence_id ON datasets (persistence_id);

CREATE TABLE distributions
(
    id             UUID                        NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at    TIMESTAMP WITHOUT TIME ZONE,
    created_by     UUID,
    modified_by    UUID,
    access_url     TEXT,
    resource_id    UUID,
    dataset_id     UUID,
    activity_id    UUID,
    api_type       VARCHAR(50),
    format         VARCHAR(100),
    auto_generated BOOLEAN                     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_distributions PRIMARY KEY (id),
    CONSTRAINT FK_DISTRIBUTIONS_ON_ACTIVITY FOREIGN KEY (activity_id) REFERENCES activities (id),
    CONSTRAINT FK_DISTRIBUTIONS_ON_DATASET FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT FK_DISTRIBUTIONS_ON_RESOURCE FOREIGN KEY (resource_id) REFERENCES resources (id)
);

CREATE INDEX idx_distribution_activity ON distributions (activity_id);
CREATE INDEX idx_distribution_dataset ON distributions (dataset_id);
CREATE INDEX idx_distribution_resource ON distributions (resource_id);

CREATE TABLE data_structure_versions
(
    id                            UUID                        NOT NULL,
    created_at                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at                   TIMESTAMP WITHOUT TIME ZONE,
    created_by                    UUID,
    modified_by                   UUID,
    description                   TEXT,
    data_structure_version_status VARCHAR(50)                 NOT NULL,
    data_structure_version_source VARCHAR(50)                 NOT NULL,
    version                       VARCHAR(50)                 NOT NULL,
    model_atlas_uri               VARCHAR(255),
    model_name                    VARCHAR(255),
    styles                        JSONB,
    data_structure_id             UUID                        NOT NULL,
    external_id                   VARCHAR(255),
    CONSTRAINT pk_data_structure_versions PRIMARY KEY (id),
    CONSTRAINT FK_DATA_STRUCTURE_VERSIONS_ON_DATA_STRUCTURE FOREIGN KEY (data_structure_id) REFERENCES data_structures (id),
    CONSTRAINT uk_data_structure_versions_data_structure_version UNIQUE (data_structure_id, version)
);

CREATE INDEX idx_data_structure_versions_data_structure ON data_structure_versions (data_structure_id);
CREATE INDEX idx_data_structure_versions_status ON data_structure_versions (data_structure_version_status);
CREATE INDEX idx_data_structure_versions_version ON data_structure_versions (version);

CREATE TABLE data_sources
(
    id                        UUID                        NOT NULL,
    name                      VARCHAR(255)                NOT NULL,
    description               TEXT,
    created_at                TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at               TIMESTAMP WITHOUT TIME ZONE,
    created_by                UUID,
    modified_by               UUID,
    data_source_status        VARCHAR(20)                 NOT NULL DEFAULT 'DRAFT',
    connector_type            VARCHAR(20),
    configuration             JSONB,
    data_structure_version_id UUID,
    CONSTRAINT pk_data_sources PRIMARY KEY (id),
    CONSTRAINT uk_data_sources_name UNIQUE (name),
    CONSTRAINT fk_data_sources_on_data_structure_version FOREIGN KEY (data_structure_version_id) REFERENCES data_structure_versions (id)
);

CREATE INDEX idx_data_sources_status ON data_sources (data_source_status);
CREATE INDEX idx_data_sources_connector_type ON data_sources (connector_type);
CREATE INDEX idx_data_sources_data_structure_version ON data_sources (data_structure_version_id);

CREATE TABLE pipelines
(
    id           UUID                        NOT NULL,
    name         VARCHAR(255)                NOT NULL,
    description  TEXT,
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at  TIMESTAMP WITHOUT TIME ZONE,
    created_by   UUID,
    modified_by  UUID,
    dataset_id   UUID                        NOT NULL,
    styles       JSONB,
    apis         TEXT[],
    persistences BIGINT[],
    model        JSONB,
    version      BIGINT                      NOT NULL DEFAULT 1,
    CONSTRAINT pk_pipelines PRIMARY KEY (id),
    CONSTRAINT FK_PIPELINES_ON_DATASET FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

CREATE INDEX idx_pipeline_dataset ON pipelines (dataset_id);

-- ----------------------------------------------------------------------------
-- Join / association tables
-- ----------------------------------------------------------------------------

CREATE TABLE group_members
(
    group_id UUID NOT NULL,
    user_id  UUID NOT NULL,
    CONSTRAINT pk_group_members PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_gromem_on_group FOREIGN KEY (group_id) REFERENCES groups (id),
    CONSTRAINT fk_gromem_on_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_group_members_user ON group_members (user_id);

CREATE TABLE role_permissions
(
    permission_id UUID NOT NULL,
    role_id       UUID NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (permission_id, role_id),
    CONSTRAINT fk_rolper_on_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_rolper_on_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX idx_role_permissions_role ON role_permissions (role_id);

CREATE TABLE activity_agents
(
    activity_id UUID NOT NULL,
    agent_id    UUID NOT NULL,
    CONSTRAINT pk_activity_agents PRIMARY KEY (activity_id, agent_id),
    CONSTRAINT fk_actage_on_activity FOREIGN KEY (activity_id) REFERENCES activities (id),
    CONSTRAINT fk_actage_on_agent FOREIGN KEY (agent_id) REFERENCES agents (id)
);

CREATE INDEX idx_activity_agents_activity ON activity_agents (activity_id);
CREATE INDEX idx_activity_agents_agent ON activity_agents (agent_id);

CREATE TABLE catalog_children
(
    child_catalog_id  UUID NOT NULL,
    parent_catalog_id UUID NOT NULL,
    CONSTRAINT pk_catalog_children PRIMARY KEY (child_catalog_id, parent_catalog_id),
    CONSTRAINT fk_catchi_on_child_catalog FOREIGN KEY (child_catalog_id) REFERENCES catalogs (id),
    CONSTRAINT fk_catchi_on_parent_catalog FOREIGN KEY (parent_catalog_id) REFERENCES catalogs (id)
);

CREATE INDEX idx_catalog_children_parent ON catalog_children (parent_catalog_id);
CREATE INDEX idx_catalog_children_child ON catalog_children (child_catalog_id);

CREATE TABLE catalog_datasets
(
    catalog_id UUID NOT NULL,
    dataset_id UUID NOT NULL,
    CONSTRAINT pk_catalog_datasets PRIMARY KEY (catalog_id, dataset_id),
    CONSTRAINT fk_catdat_on_catalog FOREIGN KEY (catalog_id) REFERENCES catalogs (id),
    CONSTRAINT fk_catdat_on_data_set FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

CREATE INDEX idx_catalog_datasets_catalog ON catalog_datasets (catalog_id);
CREATE INDEX idx_catalog_datasets_dataset ON catalog_datasets (dataset_id);

CREATE TABLE dataset_agents
(
    agent_id   UUID NOT NULL,
    dataset_id UUID NOT NULL,
    CONSTRAINT pk_dataset_agents PRIMARY KEY (agent_id, dataset_id),
    CONSTRAINT fk_datage_on_agent FOREIGN KEY (agent_id) REFERENCES agents (id),
    CONSTRAINT fk_datage_on_data_set FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

CREATE INDEX idx_dataset_agents_dataset ON dataset_agents (dataset_id);
CREATE INDEX idx_dataset_agents_agent ON dataset_agents (agent_id);

CREATE TABLE dataset_dataspaces
(
    dataset_id   UUID NOT NULL,
    dataspace_id UUID NOT NULL,
    CONSTRAINT pk_dataset_dataspaces PRIMARY KEY (dataset_id, dataspace_id),
    CONSTRAINT fk_datdat_on_data_set FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT fk_datdat_on_data_space FOREIGN KEY (dataspace_id) REFERENCES data_spaces (id)
);

CREATE INDEX idx_dataset_dataspaces_dataset ON dataset_dataspaces (dataset_id);
CREATE INDEX idx_dataset_dataspaces_dataspace ON dataset_dataspaces (dataspace_id);

CREATE TABLE pipeline_data_sources
(
    pipeline_id    UUID NOT NULL,
    data_source_id UUID NOT NULL,
    CONSTRAINT pk_pipeline_data_sources PRIMARY KEY (pipeline_id, data_source_id),
    CONSTRAINT FK_PIPELINE_DATA_SOURCES_ON_PIPELINE FOREIGN KEY (pipeline_id) REFERENCES pipelines (id),
    CONSTRAINT FK_PIPELINE_DATA_SOURCES_ON_DATA_SOURCE FOREIGN KEY (data_source_id) REFERENCES data_sources (id)
);

CREATE INDEX idx_pipeline_data_sources_pipeline ON pipeline_data_sources (pipeline_id);
CREATE INDEX idx_pipeline_data_sources_data_source ON pipeline_data_sources (data_source_id);

-- ----------------------------------------------------------------------------
-- Assignments (depends on groups, roles, and all scope-target tables)
-- ----------------------------------------------------------------------------

CREATE TABLE assignments
(
    id                UUID                        NOT NULL,
    scope_type        VARCHAR(255),
    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at       TIMESTAMP WITHOUT TIME ZONE,
    created_by        UUID,
    modified_by       UUID,
    group_id          UUID                        NOT NULL,
    role_id           UUID                        NOT NULL,
    data_structure_id UUID,
    data_source_id    UUID,
    dataset_id        UUID,
    data_space_id     UUID,
    catalog_id        UUID,
    CONSTRAINT pk_assignments PRIMARY KEY (id),
    CONSTRAINT uk_assignment_group_role_scope UNIQUE NULLS NOT DISTINCT (group_id, role_id, scope_type, data_structure_id, data_source_id, dataset_id, data_space_id, catalog_id),
    CONSTRAINT FK_ASSIGNMENTS_ON_GROUP FOREIGN KEY (group_id) REFERENCES groups (id),
    CONSTRAINT FK_ASSIGNMENTS_ON_ROLE FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT FK_ASSIGNMENTS_ON_DATASTRUCTURE FOREIGN KEY (data_structure_id) REFERENCES data_structures (id),
    CONSTRAINT FK_ASSIGNMENTS_ON_DATASOURCE FOREIGN KEY (data_source_id) REFERENCES data_sources (id),
    CONSTRAINT FK_ASSIGNMENTS_ON_DATASET FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT FK_ASSIGNMENTS_ON_DATASPACE FOREIGN KEY (data_space_id) REFERENCES data_spaces (id),
    CONSTRAINT FK_ASSIGNMENTS_ON_CATALOG FOREIGN KEY (catalog_id) REFERENCES catalogs (id)
);

CREATE INDEX idx_assignment_group ON assignments (group_id);
CREATE INDEX idx_assignment_role ON assignments (role_id);
CREATE INDEX idx_assignment_datastructure ON assignments (data_structure_id);
CREATE INDEX idx_assignment_datasource ON assignments (data_source_id);
CREATE INDEX idx_assignment_dataset ON assignments (dataset_id);
CREATE INDEX idx_assignment_dataspace ON assignments (data_space_id);
CREATE INDEX idx_assignment_catalog ON assignments (catalog_id);
CREATE INDEX idx_assignment_scope ON assignments (scope_type, data_structure_id, data_source_id, dataset_id, data_space_id, catalog_id);
