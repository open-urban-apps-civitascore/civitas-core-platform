-- Provenance journal for package installs: one header row per install, one line per artifact it
-- touched.
--
-- The lines reference artifacts by plain id and URN rather than by foreign key. That is deliberate:
-- the journal is append-only history and has to stay readable after the artifacts it names are
-- gone, which is exactly what uninstall and reference counting need later. A foreign key would
-- either block those deletions or cascade the history away with them.

CREATE TABLE installations
(
    id              UUID                        NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_by      UUID,
    modified_by     UUID,
    package_id      VARCHAR(1024),
    package_version VARCHAR(255),
    data_set_id     UUID,
    data_set_name   VARCHAR(255),
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
    action          VARCHAR(16)                 NOT NULL,
    CONSTRAINT pk_installed_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_installed_artifacts_on_installation FOREIGN KEY (installation_id) REFERENCES installations (id)
);

CREATE INDEX idx_installed_artifacts_installation ON installed_artifacts (installation_id);

-- Reference counting on uninstall asks "which other installations still name this URN?", so the
-- logical URN is the column that carries the lookup.
CREATE INDEX idx_installed_artifacts_urn ON installed_artifacts (urn);

-- "Is this package already installed here?" is the question every install answers first.
CREATE INDEX idx_installations_package ON installations (package_id);
