-- The published structures a Data structure version was built from.
--
-- A modeller can load a structure the platform publishes into the version they are editing. The
-- import pins the version the structure had, so a later version of it leaves this Data structure
-- untouched. The column records those pins, derived from the diagram on every save, so the
-- provenance is readable without opening the editor.

ALTER TABLE data_structure_versions
    ADD COLUMN IF NOT EXISTS imported_structure_urns text[];
