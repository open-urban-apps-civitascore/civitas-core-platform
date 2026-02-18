
-- change category 'SYSTEM' to 'TENANT_ADMINISTRATION'
UPDATE permissions
SET category = 'TENANT_ADMINISTRATION'
WHERE category = 'SYSTEM';

-- add new column 'source' with default value 'INTERNAL'
ALTER TABLE permissions
ADD COLUMN source VARCHAR(255) NOT NULL DEFAULT 'INTERNAL';

-- remove unique constraint uk_permission_name
ALTER TABLE permissions
DROP CONSTRAINT uk_permission_name;

-- add unique constraint uk_permission_name_source
ALTER TABLE permissions
ADD CONSTRAINT uk_permission_name_source UNIQUE (name, source);