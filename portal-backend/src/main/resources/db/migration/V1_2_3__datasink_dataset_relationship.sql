ALTER TABLE data_sinks
    ADD COLUMN dataset_id UUID;

UPDATE data_sinks
SET dataset_id = (SELECT p.dataset_id FROM pipelines p WHERE p.id = data_sinks.pipeline_id);

ALTER TABLE data_sinks
    ALTER COLUMN dataset_id SET NOT NULL;

ALTER TABLE data_sinks
    ADD CONSTRAINT fk_data_sinks_on_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id);

ALTER TABLE data_sinks
    ALTER COLUMN pipeline_id DROP NOT NULL;

CREATE INDEX idx_data_sinks_dataset ON data_sinks (dataset_id);
