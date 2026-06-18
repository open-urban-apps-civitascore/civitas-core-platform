ALTER TABLE data_sources
    ADD COLUMN datapool_scope_type VARCHAR(50) NOT NULL DEFAULT 'ALL';

CREATE TABLE data_source_datapools
(
    data_source_id UUID NOT NULL,
    datapool_id    UUID NOT NULL,
    CONSTRAINT pk_data_source_datapools PRIMARY KEY (data_source_id, datapool_id),
    CONSTRAINT fk_dsdp_data_source FOREIGN KEY (data_source_id) REFERENCES data_sources (id) ON DELETE CASCADE,
    CONSTRAINT fk_dsdp_datapool FOREIGN KEY (datapool_id) REFERENCES datapools (id) ON DELETE RESTRICT
);

CREATE INDEX idx_dsdp_datapool ON data_source_datapools (datapool_id);
