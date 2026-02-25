ALTER TABLE data_structures
    ADD COLUMN data_structure_status VARCHAR(50),
    ADD COLUMN created_from_data_source BOOLEAN NOT NULL DEFAULT false;

UPDATE data_structures
SET data_structure_status = 'DRAFT'
WHERE data_structure_status IS NULL;

ALTER TABLE data_structures
    ALTER COLUMN data_structure_status SET NOT NULL;

CREATE TABLE data_structure_versions
(
    id                              UUID                        NOT NULL,
    created_at                      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at                     TIMESTAMP WITHOUT TIME ZONE,
    created_by                      UUID,
    modified_by                     UUID,
    description                     TEXT,
    data_structure_version_status   VARCHAR(50)                 NOT NULL,
    data_structure_version_source   VARCHAR(50)                 NOT NULL,
    version                         VARCHAR(50)                 NOT NULL,
    model_atlas_uri                 VARCHAR(255),
    model_name                      VARCHAR(255),
    styles                          JSONB,
    data_structure_id               UUID                        NOT NULL,
    CONSTRAINT pk_data_structure_versions PRIMARY KEY (id),
    CONSTRAINT FK_DATA_STRUCTURE_VERSIONS_ON_DATA_STRUCTURE FOREIGN KEY (data_structure_id) REFERENCES data_structures(id)
);

CREATE INDEX idx_data_structure_versions_data_structure ON data_structure_versions (data_structure_id);
CREATE INDEX idx_data_structure_versions_status ON data_structure_versions (data_structure_version_status);
CREATE INDEX idx_data_structure_versions_version ON data_structure_versions (version);