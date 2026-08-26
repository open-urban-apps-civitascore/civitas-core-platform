-- "Bundle" collides with Model Forge's glossary term (the read-side bundled
-- view). The entity records an INSTALLATION of a catalogue entry — the service
-- layer has said so all along (InstallationService/-Controller) — so the table
-- and the catalogue-identity columns now say the same.
ALTER TABLE bundle_installations RENAME TO installations;
ALTER TABLE installations RENAME COLUMN bundle_id TO catalog_entry_id;
ALTER TABLE installations RENAME COLUMN bundle_version TO catalog_entry_version;
ALTER TABLE installations RENAME CONSTRAINT pk_bundle_installations TO pk_installations;
ALTER INDEX idx_bundle_installations_created_at RENAME TO idx_installations_created_at;
