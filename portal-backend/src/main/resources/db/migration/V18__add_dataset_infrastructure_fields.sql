ALTER TABLE datasets
    ADD COLUMN project_id       VARCHAR(255),
    ADD COLUMN frost_base_url   VARCHAR(500),
    ADD COLUMN route_id         VARCHAR(255),
    ADD COLUMN service_id       VARCHAR(255),
    ADD COLUMN public_url       VARCHAR(500),
    ADD COLUMN pipeline_ids     TEXT[],
    ADD COLUMN pending_saga_type VARCHAR(30);

ALTER TABLE pipelines ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
