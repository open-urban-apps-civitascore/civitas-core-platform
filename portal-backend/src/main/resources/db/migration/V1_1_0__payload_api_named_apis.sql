-- Payload API (#1311): replace single route_id column on datasets with a per-row named_apis child
-- table. Per concept #1379 + #1383, named APIs are dataset-level artifacts; per ADR #1362, each
-- carries {name, slug, standard, version}. The route_id is populated per-entry by the saga result
-- handler. Slug uniqueness within a dataset is enforced by uk_named_api_dataset_slug. Existing
-- route_id values are dropped because the prior single APISIX route was non-functional (concept
-- #1293).

ALTER TABLE datasets DROP COLUMN route_id;

CREATE TABLE named_apis
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    dataset_id  UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    slug        VARCHAR(32)                 NOT NULL,
    standard    VARCHAR(16)                 NOT NULL,
    version     VARCHAR(32),
    route_id    VARCHAR(255),
    CONSTRAINT pk_named_apis PRIMARY KEY (id),
    CONSTRAINT uk_named_api_dataset_slug UNIQUE (dataset_id, slug),
    CONSTRAINT fk_named_apis_on_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id) ON DELETE CASCADE
);

CREATE INDEX idx_named_api_dataset ON named_apis (dataset_id);
