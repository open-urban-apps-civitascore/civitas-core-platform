-- Seed data for AuthZ integration testing
-- Creates test permissions, roles, and role-permission mappings.
-- Users, groups, and assignments are created by the portal-backend
-- via UserInitializer (configured through environment variables).
--
-- Usage:
--   psql -h localhost -U admin -d portal_backend -f seed-authz-data.sql
--
-- Or via Docker:
--   docker exec -i civitas-postgres-portal psql -U admin -d portal_backend < seed-authz-data.sql

-- =============================================================================
-- PERMISSIONS
-- =============================================================================
-- All permissions use ENTITY_ACTION naming convention (e.g., USER_READ, DATASET_CREATE).
-- Must match permission strings in authz/rego/data/backends/portal_backend/data.json.

-- User permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'USER_READ', 'View users', 'DATA', 'USER', NOW()),
    ('10000000-0000-0000-0000-000000000002', 'USER_CREATE', 'Create users', 'DATA', 'USER', NOW()),
    ('10000000-0000-0000-0000-000000000003', 'USER_UPDATE', 'Update users', 'DATA', 'USER', NOW()),
    ('10000000-0000-0000-0000-000000000004', 'USER_DELETE', 'Delete users', 'DATA', 'USER', NOW())
ON CONFLICT (name) DO NOTHING;

-- Dataset permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000021', 'DATASET_READ', 'View datasets', 'DATA', 'DATASET', NOW()),
    ('10000000-0000-0000-0000-000000000022', 'DATASET_CREATE', 'Create datasets', 'DATA', 'DATASET', NOW()),
    ('10000000-0000-0000-0000-000000000023', 'DATASET_UPDATE', 'Update datasets', 'DATA', 'DATASET', NOW()),
    ('10000000-0000-0000-0000-000000000024', 'DATASET_DELETE', 'Delete datasets', 'DATA', 'DATASET', NOW()),
    ('10000000-0000-0000-0000-000000000025', 'DATASET_RELEASE', 'Release/unrelease datasets', 'DATA', 'DATASET', NOW())
ON CONFLICT (name) DO NOTHING;

-- Assignment permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000031', 'ASSIGNMENT_READ', 'View assignments', 'DATA', 'ASSIGNMENT', NOW()),
    ('10000000-0000-0000-0000-000000000032', 'ASSIGNMENT_CREATE', 'Create assignments', 'DATA', 'ASSIGNMENT', NOW()),
    ('10000000-0000-0000-0000-000000000034', 'ASSIGNMENT_DELETE', 'Delete assignments', 'DATA', 'ASSIGNMENT', NOW())
ON CONFLICT (name) DO NOTHING;

-- Group permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000041', 'GROUP_READ', 'View groups', 'DATA', 'GROUP', NOW()),
    ('10000000-0000-0000-0000-000000000042', 'GROUP_CREATE', 'Create groups', 'DATA', 'GROUP', NOW()),
    ('10000000-0000-0000-0000-000000000043', 'GROUP_UPDATE', 'Update groups', 'DATA', 'GROUP', NOW()),
    ('10000000-0000-0000-0000-000000000044', 'GROUP_DELETE', 'Delete groups', 'DATA', 'GROUP', NOW())
ON CONFLICT (name) DO NOTHING;

-- Role permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000051', 'ROLE_READ', 'View roles', 'DATA', 'ROLE', NOW()),
    ('10000000-0000-0000-0000-000000000052', 'ROLE_CREATE', 'Create roles', 'DATA', 'ROLE', NOW()),
    ('10000000-0000-0000-0000-000000000053', 'ROLE_UPDATE', 'Update roles', 'DATA', 'ROLE', NOW()),
    ('10000000-0000-0000-0000-000000000054', 'ROLE_DELETE', 'Delete roles', 'DATA', 'ROLE', NOW())
ON CONFLICT (name) DO NOTHING;

-- Permission permissions (read-only in Rego — only GET mapped)
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000061', 'PERMISSION_READ', 'View permissions', 'DATA', 'PERMISSION', NOW())
ON CONFLICT (name) DO NOTHING;

-- Datasource permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000081', 'DATASOURCE_READ', 'View datasources', 'DATA', 'DATASOURCE', NOW()),
    ('10000000-0000-0000-0000-000000000082', 'DATASOURCE_CREATE', 'Create datasources', 'DATA', 'DATASOURCE', NOW()),
    ('10000000-0000-0000-0000-000000000083', 'DATASOURCE_UPDATE', 'Update datasources', 'DATA', 'DATASOURCE', NOW()),
    ('10000000-0000-0000-0000-000000000084', 'DATASOURCE_DELETE', 'Delete datasources', 'DATA', 'DATASOURCE', NOW()),
    ('10000000-0000-0000-0000-000000000085', 'DATASOURCE_RELEASE', 'Release datasources', 'DATA', 'DATASOURCE', NOW())
ON CONFLICT (name) DO NOTHING;

-- Datastructure permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000091', 'DATASTRUCTURE_READ', 'View datastructures', 'DATA', 'DATASTRUCTURE', NOW()),
    ('10000000-0000-0000-0000-000000000092', 'DATASTRUCTURE_CREATE', 'Create datastructures', 'DATA', 'DATASTRUCTURE', NOW()),
    ('10000000-0000-0000-0000-000000000093', 'DATASTRUCTURE_UPDATE', 'Update datastructures', 'DATA', 'DATASTRUCTURE', NOW()),
    ('10000000-0000-0000-0000-000000000094', 'DATASTRUCTURE_DELETE', 'Delete datastructures', 'DATA', 'DATASTRUCTURE', NOW()),
    ('10000000-0000-0000-0000-000000000095', 'DATASTRUCTURE_RELEASE', 'Release datastructures', 'DATA', 'DATASTRUCTURE', NOW())
ON CONFLICT (name) DO NOTHING;

-- NOTE: Dataspace and catalog endpoints are null-permission (GET only, any authenticated user).
-- No database permissions needed for these — OPA allows them without checking permissions.

-- =============================================================================
-- ROLES
-- =============================================================================

-- DataArchitect: Full permissions (admin-level access)
INSERT INTO roles (id, name, description, role_type, created_at)
VALUES ('20000000-0000-0000-0000-000000000001', 'AuthzDataArchitect', 'Full data management permissions for authz testing', 'DATA', NOW())
ON CONFLICT (name) DO NOTHING;

-- DataConsumer: Read-only permissions
INSERT INTO roles (id, name, description, role_type, created_at)
VALUES ('20000000-0000-0000-0000-000000000002', 'AuthzDataConsumer', 'Read-only access for authz testing', 'DATA', NOW())
ON CONFLICT (name) DO NOTHING;

-- NoPerms: No permissions (for denied access testing)
INSERT INTO roles (id, name, description, role_type, created_at)
VALUES ('20000000-0000-0000-0000-000000000003', 'AuthzNoPerms', 'No permissions for authz testing', 'DATA', NOW())
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- ROLE PERMISSIONS
-- =============================================================================

-- DataArchitect gets ALL permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000001', id FROM permissions
WHERE name IN (
    'USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE',
    'DATASET_READ', 'DATASET_CREATE', 'DATASET_UPDATE', 'DATASET_DELETE', 'DATASET_RELEASE',
    'ASSIGNMENT_READ', 'ASSIGNMENT_CREATE', 'ASSIGNMENT_DELETE',
    'GROUP_READ', 'GROUP_CREATE', 'GROUP_UPDATE', 'GROUP_DELETE',
    'ROLE_READ', 'ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE',
    'PERMISSION_READ',
    'DATASOURCE_READ', 'DATASOURCE_CREATE', 'DATASOURCE_UPDATE', 'DATASOURCE_DELETE', 'DATASOURCE_RELEASE',
    'DATASTRUCTURE_READ', 'DATASTRUCTURE_CREATE', 'DATASTRUCTURE_UPDATE', 'DATASTRUCTURE_DELETE', 'DATASTRUCTURE_RELEASE'
)
ON CONFLICT DO NOTHING;

-- DataConsumer gets READ permissions only
INSERT INTO role_permissions (role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000002', id FROM permissions
WHERE name IN (
    'USER_READ',
    'DATASET_READ',
    'ASSIGNMENT_READ',
    'GROUP_READ',
    'ROLE_READ',
    'PERMISSION_READ',
    'DATASOURCE_READ',
    'DATASTRUCTURE_READ'
)
ON CONFLICT DO NOTHING;

-- NoPerms gets NO permissions (empty role)

-- =============================================================================
-- VERIFICATION QUERIES
-- =============================================================================

-- Verify data was inserted correctly
SELECT 'Permissions count:' AS info, COUNT(*) AS count FROM permissions WHERE id LIKE '10000000%';
SELECT 'Roles count:' AS info, COUNT(*) AS count FROM roles WHERE name LIKE 'Authz%';
SELECT 'Role-permissions count:' AS info, COUNT(*) AS count FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE name LIKE 'Authz%');
