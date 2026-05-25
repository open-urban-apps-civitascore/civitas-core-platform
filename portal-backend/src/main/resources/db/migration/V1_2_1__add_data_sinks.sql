CREATE TABLE data_sinks
(
    id             UUID                        NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at    TIMESTAMP WITHOUT TIME ZONE,
    created_by     UUID,
    modified_by    UUID,
    dataset_id     UUID                        NOT NULL,
    pipeline_id    UUID                        NOT NULL,
    data_sink_type VARCHAR(20)                 NOT NULL,
    configuration  JSONB,
    CONSTRAINT pk_data_sinks PRIMARY KEY (id),
    CONSTRAINT fk_data_sinks_on_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT fk_data_sinks_on_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id)
);

CREATE INDEX idx_data_sinks_dataset ON data_sinks (dataset_id);
CREATE INDEX idx_data_sinks_pipeline ON data_sinks (pipeline_id);
