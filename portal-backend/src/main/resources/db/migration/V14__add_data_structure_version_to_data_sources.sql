ALTER TABLE data_sources
    ADD COLUMN data_structure_version_id UUID;

ALTER TABLE data_sources
    ADD CONSTRAINT fk_data_sources_on_data_structure_version
    FOREIGN KEY (data_structure_version_id) REFERENCES data_structure_versions(id);

CREATE INDEX idx_data_sources_data_structure_version ON data_sources(data_structure_version_id);
