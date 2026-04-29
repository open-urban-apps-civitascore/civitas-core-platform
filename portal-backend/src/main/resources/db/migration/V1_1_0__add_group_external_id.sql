-- Add Keycloak externalId reference to groups table for group sync (#1278).
-- Unique so each portal Group maps to at most one Keycloak group.
ALTER TABLE groups ADD COLUMN external_id VARCHAR(255);
CREATE UNIQUE INDEX idx_group_external_id ON groups (external_id);
