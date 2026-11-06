-- A Data sink can be the target of several Pipelines.
CREATE TABLE pipeline_data_sinks
(
    pipeline_id  UUID NOT NULL,
    data_sink_id UUID NOT NULL,
    CONSTRAINT pk_pipeline_data_sinks PRIMARY KEY (pipeline_id, data_sink_id),
    CONSTRAINT fk_pipeline_data_sinks_on_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_data_sinks_on_data_sink FOREIGN KEY (data_sink_id) REFERENCES data_sinks (id) ON DELETE CASCADE
);
CREATE INDEX idx_pipeline_data_sinks_data_sink ON pipeline_data_sinks (data_sink_id);
INSERT INTO pipeline_data_sinks (pipeline_id, data_sink_id)
SELECT pipeline_id, id FROM data_sinks WHERE pipeline_id IS NOT NULL;

-- A Data sink without a Pipeline stays ACTIVE: SQL cannot delete its Model Forge configuration,
-- which would keep its Data structure in use, and it can be the last Data sink behind an API.

-- Drops the foreign key fk_data_sinks_on_pipeline and the index idx_data_sinks_pipeline with it.
ALTER TABLE data_sinks DROP COLUMN pipeline_id;

-- A removed Data sink whose storage exists stays as PENDING_UNPROVISION until a release removes the
-- storage. It keeps only the table name the release drops, not its Model Forge configuration.
ALTER TABLE data_sinks
    ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN unprovision_table_name VARCHAR(63),
    ADD CONSTRAINT ck_data_sinks_state CHECK (state IN ('ACTIVE', 'PENDING_UNPROVISION')),
    ADD CONSTRAINT ck_data_sinks_pending_provisioned CHECK (state = 'ACTIVE' OR provisioned),
    ADD CONSTRAINT ck_data_sinks_pending_unconfigured CHECK (state = 'ACTIVE' OR configuration_urn IS NULL),
    ADD CONSTRAINT ck_data_sinks_unprovision_table_pending CHECK (unprovision_table_name IS NULL OR state = 'PENDING_UNPROVISION');

-- A Layer goes with the API that publishes it.
ALTER TABLE layers
    ADD COLUMN named_api_id UUID,
    ADD CONSTRAINT fk_layers_on_named_api FOREIGN KEY (named_api_id) REFERENCES named_apis (id) ON DELETE CASCADE;
CREATE INDEX idx_layers_named_api ON layers (named_api_id);

UPDATE layers l
SET named_api_id = a.id
FROM named_apis a
WHERE a.dataset_id = l.dataset_id
  AND a.standard = 'OWS';
