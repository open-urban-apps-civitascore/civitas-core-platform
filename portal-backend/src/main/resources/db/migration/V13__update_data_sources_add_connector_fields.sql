ALTER TABLE data_sources
    ADD COLUMN data_source_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN connector_type VARCHAR(20),
    ADD COLUMN configuration JSONB;

CREATE INDEX idx_data_sources_status ON data_sources(data_source_status);
CREATE INDEX idx_data_sources_connector_type ON data_sources(connector_type);

ALTER TABLE data_sources
    ADD CONSTRAINT uq_data_sources_name UNIQUE (name);
