-- Provenance learns the version dimension: alongside the stable logical identity
-- (the `urn` column, which reference counting and badge matching key on), each
-- line may now record the VERSIONED urn of the artifact this install resolved —
-- the fact the update flow needs ("install A created 1.0.0") and that cannot be
-- reconstructed later once a second version exists. Nullable: sources have no
-- registry identity at all, and rows written before this migration stay honest
-- as "version unrecorded".
ALTER TABLE installed_artifacts
    ADD COLUMN versioned_urn VARCHAR(1024);
