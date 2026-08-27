-- Model Forge integration (stage 2): reference the model content stored in the model_forge
-- registry from the host shell. No foreign key across the schema boundary — the link is a
-- versioned CORE URN, kept consistent within the shared transaction.
--
-- data_structures.model_logical_urn : stable logical CORE URN, minted once by Model Forge and
--                                     reused across all versions of this data structure.
-- data_structure_versions.model_urn : versioned CORE URN pinning the concrete registry version
--                                     backing this version's model.
alter table data_structures add column model_logical_urn text;
alter table data_structure_versions add column model_urn text;

-- The version string is now assigned by Model Forge when the model is stored, so a draft
-- without a model yet has no version. (The unique constraint on (data_structure_id, version)
-- still holds — Postgres treats NULLs as distinct.)
alter table data_structure_versions alter column version drop not null;
