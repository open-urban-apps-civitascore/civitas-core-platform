-- Tracks whether a dataset's data-holding sink has been physically provisioned at least once
-- (PostGIS table / FROST project exists). Distinguishes "never released, no table" from
-- "table exists, holds data" so a destructive sink change can warn only when data is at risk.
-- Indicator for the data-loss warning, not a lifecycle gate; survives an unrelease.
ALTER TABLE datasets
    ADD COLUMN provisioned BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: any dataset that is currently AVAILABLE, or that carries a FROST project reference,
-- already has provisioned infrastructure.
UPDATE datasets
SET provisioned = TRUE
WHERE status = 'AVAILABLE'
   OR project_id IS NOT NULL;
