-- An uninstall removes the artifacts of an installation and keeps its journal entry. The time of
-- the uninstall marks the entry as history. An entry without this time is an active installation,
-- and a package can be installed again when it has no active installation.

ALTER TABLE installations
    ADD COLUMN uninstalled_at TIMESTAMP WITHOUT TIME ZONE;
