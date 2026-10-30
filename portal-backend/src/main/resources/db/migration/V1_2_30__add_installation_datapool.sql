-- An installation keeps the Data pool of the Data sources and of the Dataset that the install
-- created. The same package can then have one active installation in each Data pool. A package
-- without Data sources and without a Dataset has no Data pool, and the installation keeps null.
--
-- The column has no foreign key, for the same reason as the lines of the journal: the record must
-- stay when a user deletes the Data pool.

ALTER TABLE installations ADD COLUMN datapool_id UUID;

DROP INDEX uq_installations_active_package;

-- A Data pool has only one active installation of a package. An instance has only one active
-- installation of a package without a Data pool. A unique index treats two nulls as different
-- values, so COALESCE gives all installations without a Data pool the same value. The install
-- checks this first. Two installs of one package that run at the same time can both pass the
-- check; the index stops the second one.
CREATE UNIQUE INDEX uq_installations_active_package_datapool
    ON installations (package_id, COALESCE(datapool_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE uninstalled_at IS NULL;
