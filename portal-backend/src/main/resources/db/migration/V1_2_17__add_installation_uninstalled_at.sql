-- Uninstall support, slice 1: an installation gains one terminal timestamp.
--
-- The provenance stays a journal: lines are never touched, the header is never
-- deleted — an uninstalled installation remains readable history ("was installed
-- from 2026-08-14 to 2026-09-02"). NULL means active. Who uninstalled follows
-- from the audit column modified_by of the same write.
ALTER TABLE bundle_installations
    ADD COLUMN uninstalled_at TIMESTAMP;
