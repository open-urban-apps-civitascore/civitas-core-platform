-- An uninstall removes the artifacts of an installation and keeps its record. The record gets the
-- time of the uninstall. A package without an active installation can be installed again, so the
-- unique index covers only the active installations.

ALTER TABLE installations ADD COLUMN uninstalled_at TIMESTAMP WITHOUT TIME ZONE;

DROP INDEX uq_installations_package;

-- An instance has only one active installation of a package. The install checks this first, and
-- the check uses this index. Two installs of one package that run at the same time can both pass
-- the check; the index stops the second one.
CREATE UNIQUE INDEX uq_installations_active_package ON installations (package_id) WHERE uninstalled_at IS NULL;
