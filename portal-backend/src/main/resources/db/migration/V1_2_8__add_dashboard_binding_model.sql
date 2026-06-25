CREATE TABLE dashboards
(
    id                    UUID                        NOT NULL,
    created_at            TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at           TIMESTAMP WITHOUT TIME ZONE,
    created_by            UUID,
    modified_by           UUID,
    name                  VARCHAR(255)                NOT NULL,
    description           TEXT,
    dataset_id            UUID                        NOT NULL,
    external_dashboard_id VARCHAR(255),
    active                BOOLEAN                     NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_dashboards PRIMARY KEY (id),
    CONSTRAINT uk_dashboards_external_dashboard_id UNIQUE (external_dashboard_id),
    CONSTRAINT fk_dashboards_on_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

CREATE INDEX idx_dashboards_dataset_id ON dashboards (dataset_id);

CREATE TABLE dashboard_sources
(
    id                         UUID                        NOT NULL,
    created_at                 TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at                TIMESTAMP WITHOUT TIME ZONE,
    created_by                 UUID,
    modified_by                UUID,
    external_source_id         VARCHAR(255)                NOT NULL,
    mapped_dataset_id          UUID                        NOT NULL,
    physical_source_identifier VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_dashboard_sources PRIMARY KEY (id),
    CONSTRAINT uk_dashboard_sources_external_source_id UNIQUE (external_source_id)
);

CREATE INDEX idx_dashboard_sources_mapped_dataset_id ON dashboard_sources (mapped_dataset_id);
