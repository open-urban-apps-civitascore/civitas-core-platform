-- Provenance journal for package installs: one header row per install, one line per artifact it
-- touched.
--
-- The lines reference artifacts by plain id and URN rather than by foreign key. That is deliberate:
-- the journal is history and has to stay readable after the artifacts it names are gone, which is
-- what the uninstall relies on. A foreign key would either block those deletions or cascade the
-- history away with them.

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
    -- An uninstall removes the artifacts and keeps the entry. The time of the uninstall marks the
    -- entry as history; an entry without it is an active installation, and a package can be
    -- installed again when it has no active installation.
    uninstalled_at  TIMESTAMP WITHOUT TIME ZONE,
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
    action          VARCHAR(16)                 NOT NULL,
    CONSTRAINT pk_installed_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_installed_artifacts_on_installation FOREIGN KEY (installation_id) REFERENCES installations (id)
);

CREATE INDEX idx_installed_artifacts_installation ON installed_artifacts (installation_id);

-- Uninstall asks "which installation created this URN?", so the logical URN carries that lookup.
CREATE INDEX idx_installed_artifacts_urn ON installed_artifacts (urn);

-- Every artifact is a copy under a URN minted here; the URN it carried in its package is kept as
-- origin. Prerequisites ("is standard X installed?") and updates resolve against this column.
CREATE INDEX idx_installed_artifacts_origin ON installed_artifacts (origin);

-- A package has at most one active installation per instance. The install checks this first, but
-- two installs of one package at the same time both pass that check; this index lets the second
-- one fail. An uninstalled installation is history and does not count. The index also serves the
-- check itself.
CREATE UNIQUE INDEX uq_installations_active_package ON installations (package_id) WHERE uninstalled_at IS NULL;
