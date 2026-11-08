-- Prototype: a Superset dashboard export that the dashboard engine imports once, when it first
-- creates the dashboard. Stored outside Model Forge on purpose, to try the import path before the
-- model format is agreed.
ALTER TABLE dashboards ADD COLUMN superset_bundle jsonb;
