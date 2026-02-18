CREATE TABLE data_sources
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    CONSTRAINT pk_data_sources PRIMARY KEY (id)
);