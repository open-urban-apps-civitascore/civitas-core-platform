-- Add missing unique constraints on name for entities that previously relied on
-- application-level validation only (TOCTOU race condition).
-- GlobalExceptionHandler already maps DataIntegrityViolationException to 409.

ALTER TABLE activities ADD CONSTRAINT uk_activity_name UNIQUE (name);
ALTER TABLE catalogs ADD CONSTRAINT uk_catalog_name UNIQUE (name);
ALTER TABLE dataset_series ADD CONSTRAINT uk_dataset_series_name UNIQUE (name);
