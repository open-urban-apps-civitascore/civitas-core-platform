-- Model Forge integration (stage 4): pipeline definitions and sink/source configurations move
-- into the registry as PIPELINE / DATA_SINK / DATA_SOURCE artifacts. The host keeps thin shells
-- (FKs, status, names) plus two URN columns per entity:
--   *_logical_urn : stable logical CORE URN, minted once by Model Forge on the first store
--   *_urn         : versioned CORE URN pinning the current content version
-- No FK across the schema boundary; writes happen in the same host transaction.
-- Breaking, no backfill: portal-backend is not productive yet (concept E5).

-- Pipelines: editor-built definition (`model`) and React Flow layout (`styles`) become one
-- registry document (layout under the `x-ui-styles` keyword).
alter table pipelines add column model_logical_urn text;
alter table pipelines add column model_urn text;
alter table pipelines drop column model;
alter table pipelines drop column styles;

-- Data sinks: the type-specific configuration document (POSTGIS: tableName +
-- dataStructureVersionId soft reference, preserved verbatim inside the stored payload).
-- FROST sinks store nothing; their URN columns stay null.
alter table data_sinks add column configuration_logical_urn text;
alter table data_sinks add column configuration_urn text;
alter table data_sinks drop column configuration;

-- Data sources: the connector configuration (sensitive fields encrypted by the host before the
-- document is stored).
alter table data_sources add column configuration_logical_urn text;
alter table data_sources add column configuration_urn text;
alter table data_sources drop column configuration;

-- Datasets own no opaque JSON payload (pipeline_ids is text[], layers/styles are relational
-- entities), so no DATA_SET artifact is created in this stage.
