-- Payload API: replace the single route_id column on datasets with a named_apis child table.
-- Existing route_id values are dropped — the prior single APISIX route was non-functional and
-- there is no production data to migrate.

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
    description TEXT,
    route_id    VARCHAR(255),
    CONSTRAINT pk_named_apis PRIMARY KEY (id),
    CONSTRAINT uk_named_api_dataset_slug UNIQUE (dataset_id, slug),
    CONSTRAINT fk_named_apis_on_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id) ON DELETE CASCADE
);

CREATE INDEX idx_named_api_dataset ON named_apis (dataset_id);
