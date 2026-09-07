-- Install provenance for bundle imports: which bundle created or reused which
-- artifacts, when and by whom (audit columns). Append-only history — there are
-- deliberately no foreign keys onto the referenced artifacts, so the record
-- survives their deletion and can drive uninstall/reference counting later.
CREATE TABLE bundle_installations
(
    id             UUID                        NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at    TIMESTAMP WITHOUT TIME ZONE,
    created_by     UUID,
    modified_by    UUID,
    bundle_urn     VARCHAR(1024),
    bundle_version VARCHAR(255),
    data_set_id    UUID                        NOT NULL,
    data_set_name  VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_bundle_installations PRIMARY KEY (id)
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
    artifact_type   VARCHAR(40)                 NOT NULL,
    name            VARCHAR(255),
    shell_id        UUID,
    urn             VARCHAR(1024),
    action          VARCHAR(20)                 NOT NULL,
    CONSTRAINT pk_installed_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_installed_artifacts_on_installation FOREIGN KEY (installation_id) REFERENCES bundle_installations (id)
);

-- Postgres does not index FK columns automatically; this backs the per-install
-- artifact fetch and the FK check on parent deletes.
CREATE INDEX idx_installed_artifacts_installation ON installed_artifacts (installation_id);

-- Newest-first listing is the only defined access path.
CREATE INDEX idx_bundle_installations_created_at ON bundle_installations (created_at DESC);
