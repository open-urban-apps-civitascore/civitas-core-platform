-- The pin of a structure a sink port publishes.
--
-- The structures themselves are generated at build time and shipped as resources. This table holds
-- only what the registry decides: the logical identity and the version it assigned. Without it the
-- host cannot tell a restart from a changed structure, and every start would mint a new version.
--
-- content_hash is the hash of the rendered document. It is what decides whether the declaration
-- changed; the stored artifact cannot be compared directly, because the registry splits it into
-- its member Elements and stamps its own self-description on it.

CREATE TABLE published_structures
(
    id            UUID                        NOT NULL,
    created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    modified_at   TIMESTAMP WITHOUT TIME ZONE,
    created_by    UUID,
    modified_by   UUID,
    port          VARCHAR(255)                NOT NULL,
    logical_urn   TEXT                        NOT NULL,
    versioned_urn TEXT                        NOT NULL,
    content_hash  VARCHAR(64)                 NOT NULL,
    CONSTRAINT pk_published_structures PRIMARY KEY (id),
    CONSTRAINT uk_published_structures_port UNIQUE (port)
);
