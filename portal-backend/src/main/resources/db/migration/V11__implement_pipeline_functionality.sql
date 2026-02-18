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
    apis         text[],
    persistences bigint[],
    model        JSONB,
    CONSTRAINT pk_pipelines PRIMARY KEY (id)
);

ALTER TABLE pipelines
    ADD CONSTRAINT FK_PIPELINES_ON_DATASET FOREIGN KEY (dataset_id) REFERENCES datasets (id);

CREATE INDEX idx_pipeline_dataset ON pipelines (dataset_id);

CREATE TABLE pipeline_data_sources
(
    pipeline_id    UUID NOT NULL,
    data_source_id UUID NOT NULL,
    CONSTRAINT pk_pipeline_data_sources PRIMARY KEY (pipeline_id, data_source_id)
);

ALTER TABLE pipeline_data_sources
    ADD CONSTRAINT FK_PIPELINE_DATA_SOURCES_ON_PIPELINE FOREIGN KEY (pipeline_id) REFERENCES pipelines (id);

ALTER TABLE pipeline_data_sources
    ADD CONSTRAINT FK_PIPELINE_DATA_SOURCES_ON_DATA_SOURCE FOREIGN KEY (data_source_id) REFERENCES data_sources (id);

CREATE INDEX idx_pipeline_data_sources_pipeline ON pipeline_data_sources (pipeline_id);
CREATE INDEX idx_pipeline_data_sources_data_source ON pipeline_data_sources (data_source_id);

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

