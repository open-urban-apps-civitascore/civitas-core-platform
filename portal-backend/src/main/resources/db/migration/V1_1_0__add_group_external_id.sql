ALTER TABLE groups ADD COLUMN external_id VARCHAR(255);
CREATE UNIQUE INDEX idx_group_external_id ON groups (external_id);
