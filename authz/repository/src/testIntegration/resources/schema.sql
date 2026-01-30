-- Schema for AuthZ Adapter integration tests
-- Based on portal-backend V1 migration

CREATE TABLE IF NOT EXISTS users
(
    id          UUID                        NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    title       VARCHAR(255)                NOT NULL DEFAULT 'OTHER',
    first_name  VARCHAR(255)                NOT NULL,
    last_name   VARCHAR(255)                NOT NULL,
    email       VARCHAR(255)                NOT NULL,
    phone       VARCHAR(255),
    external_id VARCHAR(255),
    active      BOOLEAN                     NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS groups
(
    id              UUID                        NOT NULL,
    name            VARCHAR(255)                NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_by      UUID,
    modified_by     UUID,
    contact_user_id UUID,
    parent_group_id UUID,
    CONSTRAINT pk_groups PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS permissions
(
    id              UUID                        NOT NULL,
    name            VARCHAR(255)                NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_by      UUID,
    modified_by     UUID,
    permission_type VARCHAR(255)                NOT NULL,
    category        VARCHAR(255)                NOT NULL DEFAULT 'GENERAL',
    CONSTRAINT pk_permissions PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS roles
(
    id          UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITHOUT TIME ZONE,
    created_by  UUID,
    modified_by UUID,
    role_type   VARCHAR(255)                NOT NULL,
    readonly    BOOLEAN                     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_roles PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS assignments
(
    id                   UUID                        NOT NULL,
    scope_type           VARCHAR(255)                NOT NULL,
    scope_id             VARCHAR(255),
    created_at           TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at          TIMESTAMP WITHOUT TIME ZONE,
    created_by           UUID,
    modified_by          UUID,
    group_id             UUID                        NOT NULL,
    role_id              UUID                        NOT NULL,
    is_inherited         BOOLEAN                     NOT NULL,
    parent_assignment_id UUID,
    CONSTRAINT pk_assignments PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS group_members
(
    group_id UUID NOT NULL,
    user_id  UUID NOT NULL,
    CONSTRAINT pk_group_members PRIMARY KEY (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS role_permissions
(
    permission_id UUID NOT NULL,
    role_id       UUID NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (permission_id, role_id)
);

-- Foreign keys
ALTER TABLE assignments
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_GROUP FOREIGN KEY (group_id) REFERENCES groups (id);

ALTER TABLE assignments
    ADD CONSTRAINT FK_ASSIGNMENTS_ON_ROLE FOREIGN KEY (role_id) REFERENCES roles (id);

ALTER TABLE group_members
    ADD CONSTRAINT fk_gromem_on_group FOREIGN KEY (group_id) REFERENCES groups (id);

ALTER TABLE group_members
    ADD CONSTRAINT fk_gromem_on_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_rolper_on_permission FOREIGN KEY (permission_id) REFERENCES permissions (id);

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_rolper_on_role FOREIGN KEY (role_id) REFERENCES roles (id);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_user_external_id ON users (external_id);
CREATE INDEX IF NOT EXISTS idx_assignment_group ON assignments (group_id);
CREATE INDEX IF NOT EXISTS idx_assignment_role ON assignments (role_id);
