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

CREATE INDEX idx_dashboards_external_dashboard_id ON dashboards (external_dashboard_id);
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

CREATE INDEX idx_dashboard_sources_external_source_id ON dashboard_sources (external_source_id);
CREATE INDEX idx_dashboard_sources_mapped_dataset_id ON dashboard_sources (mapped_dataset_id);

CREATE TABLE dashboard_dashboard_sources
(
    dashboard_id        UUID NOT NULL,
    dashboard_source_id UUID NOT NULL,
    CONSTRAINT pk_dashboard_dashboard_sources PRIMARY KEY (dashboard_id, dashboard_source_id),
    CONSTRAINT fk_dds_on_dashboard FOREIGN KEY (dashboard_id) REFERENCES dashboards (id) ON DELETE CASCADE,
    CONSTRAINT fk_dds_on_dashboard_source FOREIGN KEY (dashboard_source_id) REFERENCES dashboard_sources (id) ON DELETE CASCADE
);

-- The composite PRIMARY KEY already indexes the leading dashboard_id, so only the trailing
-- column needs its own index for reverse lookups.
CREATE INDEX idx_dds_dashboard_source ON dashboard_dashboard_sources (dashboard_source_id);
