-- A use case keeps the Data pool of the Data sources and of the Dataset that the install created.
-- The same package can then have one active use case in each Data pool. A package without Data
-- sources and without a Dataset has no Data pool, and the use case keeps null.
--
-- The column has no foreign key, for the same reason as the lines of the journal: the record must
-- stay when a user deletes the Data pool.

ALTER TABLE use_cases ADD COLUMN datapool_id UUID;

DROP INDEX uq_use_cases_active_package;

-- A Data pool has only one active use case of a package. An instance has only one active use case
-- of a package without a Data pool. A unique index treats two nulls as different values, so
-- COALESCE gives all use cases without a Data pool the same value. The install checks this first.
-- Two installs of one package that run at the same time can both pass the check; the index stops
-- the second one.
CREATE UNIQUE INDEX uq_use_cases_active_package_datapool
    ON use_cases (package_id, COALESCE(datapool_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE uninstalled_at IS NULL;
