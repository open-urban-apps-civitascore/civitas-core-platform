-- DataSet manifest artifact URN.
--
-- A DataSet is now backed by a Model Forge CORE artifact (a "manifest": a dataset-ref library that
-- references its member artifacts — pipelines and, transitively, their sources/sinks/mappings/
-- data structures). These columns pin that manifest: manifest_logical_urn is the version-free
-- identity, manifest_urn the current versioned pin. Populated when a dataset is created; null for
-- pre-existing rows (their manifest is created lazily on the next save).

ALTER TABLE datasets ADD COLUMN IF NOT EXISTS manifest_logical_urn text;
ALTER TABLE datasets ADD COLUMN IF NOT EXISTS manifest_urn text;
