UPDATE activities SET created_by = NULL WHERE created_by = 'system';

UPDATE activities
SET
    modified_by = NULL
WHERE
    modified_by = 'system';

ALTER TABLE activities
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE agents SET created_by = NULL WHERE created_by = 'system';

UPDATE agents SET modified_by = NULL WHERE modified_by = 'system';

ALTER TABLE agents
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE assignments SET created_by = NULL WHERE created_by = 'system';

UPDATE assignments
SET
    modified_by = NULL
WHERE
    modified_by = 'system';

ALTER TABLE assignments
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE catalogs SET created_by = NULL WHERE created_by = 'system';

UPDATE catalogs SET modified_by = NULL WHERE modified_by = 'system';

ALTER TABLE catalogs
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE data_spaces SET created_by = NULL WHERE created_by = 'system';

UPDATE data_spaces
SET
    modified_by = NULL
WHERE
    modified_by = 'system';

ALTER TABLE data_spaces
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE dataset_series
SET
    created_by = NULL
WHERE
    created_by = 'system';

UPDATE dataset_series
SET
    modified_by = NULL
WHERE
    modified_by = 'system';

ALTER TABLE dataset_series
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE datasets SET created_by = NULL WHERE created_by = 'system';

UPDATE datasets SET modified_by = NULL WHERE modified_by = 'system';

ALTER TABLE datasets
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE distributions
SET
    created_by = NULL
WHERE
    created_by = 'system';

UPDATE distributions
SET
    modified_by = NULL
WHERE
    modified_by = 'system';

ALTER TABLE distributions
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE groups SET created_by = NULL WHERE created_by = 'system';

UPDATE groups SET modified_by = NULL WHERE modified_by = 'system';

ALTER TABLE groups
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE permissions SET created_by = NULL WHERE created_by = 'system';

UPDATE permissions
SET
    modified_by = NULL
WHERE
    modified_by = 'system';

ALTER TABLE permissions
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE resources SET created_by = NULL WHERE created_by = 'system';

UPDATE resources SET modified_by = NULL WHERE modified_by = 'system';

ALTER TABLE resources
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE roles SET created_by = NULL WHERE created_by = 'system';

UPDATE roles SET modified_by = NULL WHERE modified_by = 'system';

ALTER TABLE roles
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;

UPDATE users SET created_by = NULL WHERE created_by = 'system';

UPDATE users SET modified_by = NULL WHERE modified_by = 'system';

ALTER TABLE users
ALTER COLUMN created_by TYPE UUID USING created_by::UUID,
ALTER COLUMN modified_by TYPE UUID USING modified_by::UUID;