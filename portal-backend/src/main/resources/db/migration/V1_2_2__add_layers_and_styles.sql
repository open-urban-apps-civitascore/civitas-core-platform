CREATE TABLE styles
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    dataset_id  UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    sld_content TEXT                        NOT NULL,
    CONSTRAINT pk_styles PRIMARY KEY (id),
    CONSTRAINT uk_styles_dataset_name UNIQUE (dataset_id, name),
    CONSTRAINT fk_styles_on_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id)
);

CREATE INDEX idx_styles_dataset ON styles (dataset_id);

CREATE TABLE layers
(
    id                   UUID                        NOT NULL,
    created_at           TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at          TIMESTAMP WITHOUT TIME ZONE,
    created_by           UUID,
    modified_by          UUID,
    dataset_id           UUID                        NOT NULL,
    datasink_id          UUID                        NOT NULL,
    layer_name           VARCHAR(255)                NOT NULL,
    title                VARCHAR(255),
    description          TEXT,
    keywords             TEXT[],
    attribute            TEXT[],
    geometry_column_ref  VARCHAR(255),
    cql_filter           TEXT,
    default_style_id     UUID,
    crs                  VARCHAR(255),
    bbox_auto_calculate  BOOLEAN                     NOT NULL DEFAULT TRUE,
    native_bounding_box  JSONB,
    lat_lon_bounding_box JSONB,
    CONSTRAINT pk_layers PRIMARY KEY (id),
    CONSTRAINT uk_layers_datasink_layer_name UNIQUE (datasink_id, layer_name),
    CONSTRAINT fk_layers_on_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT fk_layers_on_datasink FOREIGN KEY (datasink_id) REFERENCES data_sinks (id),
    CONSTRAINT fk_layers_on_default_style FOREIGN KEY (default_style_id) REFERENCES styles (id) ON DELETE SET NULL
);

CREATE INDEX idx_layers_dataset ON layers (dataset_id);
CREATE INDEX idx_layers_datasink ON layers (datasink_id);
CREATE INDEX idx_layers_default_style ON layers (default_style_id);

CREATE TABLE layer_alternative_styles
(
    layer_id UUID NOT NULL,
    style_id UUID NOT NULL,
    CONSTRAINT pk_layer_alternative_styles PRIMARY KEY (layer_id, style_id),
    CONSTRAINT fk_las_on_layer FOREIGN KEY (layer_id) REFERENCES layers (id) ON DELETE CASCADE,
    CONSTRAINT fk_las_on_style FOREIGN KEY (style_id) REFERENCES styles (id) ON DELETE CASCADE
);

CREATE INDEX idx_las_style ON layer_alternative_styles (style_id);
