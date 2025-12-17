CREATE TABLE activities
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255),
    CONSTRAINT pk_activities PRIMARY KEY (id)
);

CREATE TABLE activity_agents
(
    activity_id UUID NOT NULL,
    agent_id    UUID NOT NULL,
    CONSTRAINT pk_activity_agents PRIMARY KEY (activity_id, agent_id)
);

CREATE TABLE agents
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255),
    name        VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_agents PRIMARY KEY (id)
);

CREATE TABLE catalog_children
(
    child_catalog_id  UUID NOT NULL,
    parent_catalog_id UUID NOT NULL,
    CONSTRAINT pk_catalog_children PRIMARY KEY (child_catalog_id, parent_catalog_id)
);

CREATE TABLE catalog_datasets
(
    catalog_id UUID NOT NULL,
    dataset_id UUID NOT NULL,
    CONSTRAINT pk_catalog_datasets PRIMARY KEY (catalog_id, dataset_id)
);

CREATE TABLE catalogs
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255),
    CONSTRAINT pk_catalogs PRIMARY KEY (id)
);

CREATE TABLE dataset_agents
(
    agent_id   UUID NOT NULL,
    dataset_id UUID NOT NULL,
    CONSTRAINT pk_dataset_agents PRIMARY KEY (agent_id, dataset_id)
);

CREATE TABLE dataset_series
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255),
    CONSTRAINT pk_dataset_series PRIMARY KEY (id)
);

CREATE TABLE distributions
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255),
    access_url  TEXT,
    resource_id UUID,
    dataset_id  UUID,
    activity_id UUID,
    CONSTRAINT pk_distributions PRIMARY KEY (id)
);

CREATE TABLE resources
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255),
    CONSTRAINT pk_resources PRIMARY KEY (id)
);

ALTER TABLE datasets
    ADD dataset_series_id UUID;

ALTER TABLE datasets
    ADD identifier VARCHAR(255);

ALTER TABLE datasets
    ADD version VARCHAR(255);

ALTER TABLE datasets
    ADD CONSTRAINT FK_DATASETS_ON_DATASET_SERIES FOREIGN KEY (dataset_series_id) REFERENCES dataset_series (id);

ALTER TABLE distributions
    ADD CONSTRAINT FK_DISTRIBUTIONS_ON_ACTIVITY FOREIGN KEY (activity_id) REFERENCES activities (id);

ALTER TABLE distributions
    ADD CONSTRAINT FK_DISTRIBUTIONS_ON_DATASET FOREIGN KEY (dataset_id) REFERENCES datasets (id);

ALTER TABLE distributions
    ADD CONSTRAINT FK_DISTRIBUTIONS_ON_RESOURCE FOREIGN KEY (resource_id) REFERENCES resources (id);

ALTER TABLE activity_agents
    ADD CONSTRAINT fk_actage_on_activity FOREIGN KEY (activity_id) REFERENCES activities (id);

ALTER TABLE activity_agents
    ADD CONSTRAINT fk_actage_on_agent FOREIGN KEY (agent_id) REFERENCES agents (id);

ALTER TABLE catalog_children
    ADD CONSTRAINT fk_catchi_on_child_catalog FOREIGN KEY (child_catalog_id) REFERENCES catalogs (id);

ALTER TABLE catalog_children
    ADD CONSTRAINT fk_catchi_on_parent_catalog FOREIGN KEY (parent_catalog_id) REFERENCES catalogs (id);

ALTER TABLE catalog_datasets
    ADD CONSTRAINT fk_catdat_on_catalog FOREIGN KEY (catalog_id) REFERENCES catalogs (id);

ALTER TABLE catalog_datasets
    ADD CONSTRAINT fk_catdat_on_data_set FOREIGN KEY (dataset_id) REFERENCES datasets (id);

ALTER TABLE dataset_agents
    ADD CONSTRAINT fk_datage_on_agent FOREIGN KEY (agent_id) REFERENCES agents (id);

ALTER TABLE dataset_agents
    ADD CONSTRAINT fk_datage_on_data_set FOREIGN KEY (dataset_id) REFERENCES datasets (id);

CREATE INDEX idx_dataset_series ON datasets (dataset_series_id);

CREATE INDEX idx_distribution_activity ON distributions (activity_id);

CREATE INDEX idx_distribution_dataset ON distributions (dataset_id);

CREATE INDEX idx_distribution_resource ON distributions (resource_id);