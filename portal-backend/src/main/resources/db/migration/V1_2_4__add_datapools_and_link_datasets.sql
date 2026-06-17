CREATE TABLE datapools
(
    id                     UUID                        NOT NULL,
    name                   VARCHAR(255)                NOT NULL,
    description            TEXT,
    contact_person_user_id UUID,
    created_at             TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at            TIMESTAMP WITHOUT TIME ZONE,
    created_by             UUID,
    modified_by            UUID,
    CONSTRAINT pk_datapools PRIMARY KEY (id),
    CONSTRAINT fk_datapools_on_contact_person FOREIGN KEY (contact_person_user_id) REFERENCES users (id)
);

CREATE INDEX idx_datapool_contact_person ON datapools (contact_person_user_id);

ALTER TABLE datasets
    ADD COLUMN datapool_id UUID;

ALTER TABLE datasets
    ADD CONSTRAINT fk_datasets_on_datapool FOREIGN KEY (datapool_id) REFERENCES datapools (id);

CREATE INDEX idx_dataset_datapool ON datasets (datapool_id);

ALTER TABLE assignments
    ADD COLUMN datapool_id UUID;

ALTER TABLE assignments
    ADD CONSTRAINT fk_assignments_on_datapool FOREIGN KEY (datapool_id) REFERENCES datapools (id);

CREATE INDEX idx_assignment_datapool ON assignments (datapool_id);

ALTER TABLE assignments
    DROP CONSTRAINT uk_assignment_group_role_scope;

ALTER TABLE assignments
    ADD CONSTRAINT uk_assignment_group_role_scope UNIQUE NULLS NOT DISTINCT (group_id, role_id,
        scope_type, data_structure_id, data_source_id, dataset_id, data_space_id, catalog_id,
        datapool_id);

DROP INDEX idx_assignment_scope;

CREATE INDEX idx_assignment_scope ON assignments (scope_type, data_structure_id, data_source_id,
    dataset_id, data_space_id, catalog_id, datapool_id);
