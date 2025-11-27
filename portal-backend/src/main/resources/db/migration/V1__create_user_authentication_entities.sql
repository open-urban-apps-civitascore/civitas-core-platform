CREATE TABLE assignments
(
    id                   UUID                        NOT NULL,
    scope_type           VARCHAR(255)                NOT NULL,
    scope_id             VARCHAR(255),
    created_at           TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at          TIMESTAMP WITHOUT TIME ZONE,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255),
    group_id             UUID                        NOT NULL,
    role_id              UUID                        NOT NULL,
    is_inherited         BOOLEAN                     NOT NULL,
    parent_assignment_id UUID,
    CONSTRAINT pk_assignments PRIMARY KEY (id)
);

CREATE TABLE data_spaces
(
    id                  UUID                        NOT NULL,
    name                VARCHAR(255)                NOT NULL,
    description         TEXT,
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at         TIMESTAMP WITHOUT TIME ZONE,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255),
    owner_user_id       UUID,
    parent_dataspace_id UUID,
    external_id         VARCHAR(255),
    CONSTRAINT pk_data_spaces PRIMARY KEY (id)
);

CREATE TABLE dataset_dataspaces
(
    dataset_id   UUID NOT NULL,
    dataspace_id UUID NOT NULL,
    CONSTRAINT pk_dataset_dataspaces PRIMARY KEY (dataset_id, dataspace_id)
);

CREATE TABLE datasets
(
    id            UUID                        NOT NULL,
    name          VARCHAR(255)                NOT NULL,
    description   TEXT,
    created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at   TIMESTAMP WITHOUT TIME ZONE,
    created_by    VARCHAR(255),
    modified_by   VARCHAR(255),
    owner_user_id UUID,
    external_id   VARCHAR(255),
    format        VARCHAR(255),
    CONSTRAINT pk_datasets PRIMARY KEY (id)
);

CREATE TABLE group_members
(
    group_id UUID NOT NULL,
    user_id  UUID NOT NULL,
    CONSTRAINT pk_group_members PRIMARY KEY (group_id, user_id)
);

CREATE TABLE group_roles
(
    group_id UUID NOT NULL,
    role_id  UUID NOT NULL,
    CONSTRAINT pk_group_roles PRIMARY KEY (group_id, role_id)
);

CREATE TABLE groups
(
    id              UUID                        NOT NULL,
    name            VARCHAR(255)                NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255),
    contact_user_id UUID,
    parent_group_id UUID,
    CONSTRAINT pk_groups PRIMARY KEY (id)
);

CREATE TABLE permissions
(
    id              UUID                        NOT NULL,
    name            VARCHAR(255)                NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255),
    permission_type VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_permissions PRIMARY KEY (id)
);

CREATE TABLE role_permissions
(
    permission_id UUID NOT NULL,
    role_id       UUID NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (permission_id, role_id)
);

CREATE TABLE roles
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255),
    role_type   VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id)
);

CREATE TABLE users
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255),
    first_name  VARCHAR(255)                NOT NULL,
    last_name   VARCHAR(255)                NOT NULL,
    email       VARCHAR(255)                NOT NULL,
    phone       VARCHAR(255),
    external_id VARCHAR(255),
    active      BOOLEAN                     NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE assignments
    ADD CONSTRAINT uk_assignment_group_role_scope UNIQUE (group_id, role_id, scope_type, scope_id);

ALTER TABLE datasets
    ADD CONSTRAINT uk_dataset_name UNIQUE (name);

ALTER TABLE data_spaces
    ADD CONSTRAINT uk_dataspace_name UNIQUE (name);

ALTER TABLE groups
    ADD CONSTRAINT uk_group_name UNIQUE (name);

ALTER TABLE permissions
    ADD CONSTRAINT uk_permission_name UNIQUE (name);

ALTER TABLE roles
    ADD CONSTRAINT uk_role_name UNIQUE (name);

ALTER TABLE users
    ADD CONSTRAINT uk_user_email UNIQUE (email);

CREATE INDEX idx_assignment_scope ON assignments (scope_type, scope_id);

CREATE INDEX idx_dataset_external_id ON datasets (external_id);

CREATE INDEX idx_permission_type ON permissions (permission_type);

CREATE INDEX idx_role_type ON roles (role_type);

CREATE INDEX idx_user_active ON users (active);

CREATE INDEX idx_user_email ON users (email);

CREATE INDEX idx_user_external_id ON users (external_id);

ALTER TABLE assignments
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_GROUP FOREIGN KEY (group_id) REFERENCES groups (id);

CREATE INDEX idx_assignment_group ON assignments (group_id);

ALTER TABLE assignments
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_PARENT_ASSIGNMENT FOREIGN KEY (parent_assignment_id) REFERENCES assignments (id);

ALTER TABLE assignments
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_ROLE FOREIGN KEY (role_id) REFERENCES roles (id);

CREATE INDEX idx_assignment_role ON assignments (role_id);

ALTER TABLE datasets
    ADD CONSTRAINT FK_DATASETS_ON_OWNER_USER FOREIGN KEY (owner_user_id) REFERENCES users (id);

CREATE INDEX idx_dataset_owner ON datasets (owner_user_id);

ALTER TABLE data_spaces
    ADD CONSTRAINT FK_DATA_SPACES_ON_OWNER_USER FOREIGN KEY (owner_user_id) REFERENCES users (id);

CREATE INDEX idx_dataspace_owner ON data_spaces (owner_user_id);

ALTER TABLE data_spaces
    ADD CONSTRAINT FK_DATA_SPACES_ON_PARENT_DATASPACE FOREIGN KEY (parent_dataspace_id) REFERENCES data_spaces (id);

ALTER TABLE groups
    ADD CONSTRAINT FK_GROUPS_ON_CONTACT_USER FOREIGN KEY (contact_user_id) REFERENCES users (id);

CREATE INDEX idx_group_contact ON groups (contact_user_id);

ALTER TABLE groups
    ADD CONSTRAINT FK_GROUPS_ON_PARENT_GROUP FOREIGN KEY (parent_group_id) REFERENCES groups (id);

ALTER TABLE dataset_dataspaces
    ADD CONSTRAINT fk_datdat_on_data_set FOREIGN KEY (dataset_id) REFERENCES datasets (id);

ALTER TABLE dataset_dataspaces
    ADD CONSTRAINT fk_datdat_on_data_space FOREIGN KEY (dataspace_id) REFERENCES data_spaces (id);

ALTER TABLE group_members
    ADD CONSTRAINT fk_gromem_on_group FOREIGN KEY (group_id) REFERENCES groups (id);

ALTER TABLE group_members
    ADD CONSTRAINT fk_gromem_on_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE group_roles
    ADD CONSTRAINT fk_grorol_on_group FOREIGN KEY (group_id) REFERENCES groups (id);

ALTER TABLE group_roles
    ADD CONSTRAINT fk_grorol_on_role FOREIGN KEY (role_id) REFERENCES roles (id);

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_rolper_on_permission FOREIGN KEY (permission_id) REFERENCES permissions (id);

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_rolper_on_role FOREIGN KEY (role_id) REFERENCES roles (id);