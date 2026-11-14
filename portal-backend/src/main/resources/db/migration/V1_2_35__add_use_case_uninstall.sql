-- An uninstall removes the artifacts of a use case and keeps its record. The record gets the time
-- of the uninstall. A package without an active use case can be installed again, so the unique
-- index covers only the active use cases.

ALTER TABLE use_cases ADD COLUMN uninstalled_at TIMESTAMP WITHOUT TIME ZONE;

DROP INDEX uq_use_cases_package;

-- An instance has only one active use case of a package. The install checks this first, and the
-- check uses this index. Two installs of one package that run at the same time can both pass the
-- check; the index stops the second one.
CREATE UNIQUE INDEX uq_use_cases_active_package ON use_cases (package_id) WHERE uninstalled_at IS NULL;
