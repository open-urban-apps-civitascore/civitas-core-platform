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
    styles       TEXT,
    data_sources bigint[],
    apis         text[],
    persistences bigint[],
    model        TEXT,
    CONSTRAINT pk_pipelines PRIMARY KEY (id)
);

ALTER TABLE pipelines
    ADD CONSTRAINT FK_PIPELINES_ON_DATASET FOREIGN KEY (dataset_id) REFERENCES datasets (id);

CREATE INDEX idx_pipeline_dataset ON pipelines (dataset_id);

ALTER TABLE datasets
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE datasets
    ADD COLUMN persistence_id BIGINT;

ALTER TABLE datasets
    ADD COLUMN open_data_access BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE distributions
    ADD COLUMN api_type VARCHAR(50);

ALTER TABLE distributions
    ADD COLUMN format VARCHAR(100);

ALTER TABLE distributions
    ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_dataset_status ON datasets (status);
CREATE INDEX idx_dataset_persistence_id ON datasets (persistence_id);

