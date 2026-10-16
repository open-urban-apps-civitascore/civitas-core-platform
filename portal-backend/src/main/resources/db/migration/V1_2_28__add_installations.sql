-- Journal of package installs: one row for each install, and one line for each artifact that the
-- install created.
--
-- A line names its artifact by id and URN, not by a foreign key. The journal is a record of what
-- happened, so it must stay when a user deletes an artifact that it names. A foreign key would
-- block the deletion or delete the line together with the artifact.

CREATE TABLE installations
(
    id              UUID                        NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_by      UUID,
    modified_by     UUID,
    package_id      VARCHAR(1024)               NOT NULL,
    package_version VARCHAR(255),
    CONSTRAINT pk_installations PRIMARY KEY (id)
);

CREATE TABLE installed_artifacts
(
    id              UUID                        NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_by      UUID,
    modified_by     UUID,
    installation_id UUID                        NOT NULL,
    position        INTEGER                     NOT NULL,
    artifact_type   VARCHAR(32)                 NOT NULL,
    name            VARCHAR(255),
    shell_id        UUID,
    urn             VARCHAR(1024),
    versioned_urn   VARCHAR(1024),
    origin          VARCHAR(1024),
    CONSTRAINT pk_installed_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_installed_artifacts_on_installation FOREIGN KEY (installation_id) REFERENCES installations (id)
);

CREATE INDEX idx_installed_artifacts_installation ON installed_artifacts (installation_id);

-- An instance has only one installation of a package. The install checks this first, and the
-- check uses this index. Two installs of one package that run at the same time can both pass the
-- check; the index stops the second one.
CREATE UNIQUE INDEX uq_installations_package ON installations (package_id);
