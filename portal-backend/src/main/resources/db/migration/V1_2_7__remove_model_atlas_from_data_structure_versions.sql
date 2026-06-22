-- Replace the external Reference with a JSON Schema persisted directly on the version.

ALTER TABLE data_structure_versions
    ADD COLUMN model jsonb;

ALTER TABLE data_structure_versions
    DROP COLUMN model_atlas_uri;

ALTER TABLE data_structure_versions
    DROP COLUMN external_id;
