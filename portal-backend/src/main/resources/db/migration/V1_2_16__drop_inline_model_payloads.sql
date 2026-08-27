-- Model Forge integration (stage 3): the registry is the source of truth for the model content.
-- The inline jsonb payloads on the version shell are dropped:
--   model  -> served from Model Forge via the version's model_urn pin (bundled schema view)
--   styles -> live inside the stored schema document under the top-level `x-ui-styles` keyword
--             (Model Forge's schema fidelity preserves unknown x-* keywords verbatim)
-- Breaking, no backfill: portal-backend is not productive yet (concept E2/E5).
alter table data_structure_versions drop column model;
alter table data_structure_versions drop column styles;
