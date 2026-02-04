-- M5: Seed data for AuthZ integration testing
-- Creates test users, permissions, roles, groups, and assignments
--
-- Test Users:
--   - authz.admin: Full permissions (DataArchitect role)
--   - authz.reader: Read-only permissions (DataConsumer role)
--   - authz.none: No permissions (NoPerms role)
--
-- Usage:
--   psql -h localhost -U admin -d portal_backend -f seed-authz-data.sql
--
-- Or via Docker:
--   docker exec -i civitas-postgres-portal psql -U admin -d portal_backend < seed-authz-data.sql

-- =============================================================================
-- PERMISSIONS
-- =============================================================================
-- All permissions from portal_backend/data.json

-- User permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'READ_USER', 'View users', 'DATA', 'USER', NOW()),
    ('10000000-0000-0000-0000-000000000002', 'CREATE_USER', 'Create users', 'DATA', 'USER', NOW()),
    ('10000000-0000-0000-0000-000000000003', 'UPDATE_USER', 'Update users', 'DATA', 'USER', NOW()),
    ('10000000-0000-0000-0000-000000000004', 'DELETE_USER', 'Delete users', 'DATA', 'USER', NOW())
ON CONFLICT (name) DO NOTHING;

-- Dataspace permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000011', 'READ_DATASPACE', 'View dataspaces', 'DATA', 'DATASPACE', NOW()),
    ('10000000-0000-0000-0000-000000000012', 'CREATE_DATASPACE', 'Create dataspaces', 'DATA', 'DATASPACE', NOW()),
    ('10000000-0000-0000-0000-000000000013', 'UPDATE_DATASPACE', 'Update dataspaces', 'DATA', 'DATASPACE', NOW()),
    ('10000000-0000-0000-0000-000000000014', 'DELETE_DATASPACE', 'Delete dataspaces', 'DATA', 'DATASPACE', NOW())
ON CONFLICT (name) DO NOTHING;

-- Dataset permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000021', 'READ_DATASET', 'View datasets', 'DATA', 'DATASET', NOW()),
    ('10000000-0000-0000-0000-000000000022', 'CREATE_DATASET', 'Create datasets', 'DATA', 'DATASET', NOW()),
    ('10000000-0000-0000-0000-000000000023', 'UPDATE_DATASET', 'Update datasets', 'DATA', 'DATASET', NOW()),
    ('10000000-0000-0000-0000-000000000024', 'DELETE_DATASET', 'Delete datasets', 'DATA', 'DATASET', NOW())
ON CONFLICT (name) DO NOTHING;

-- Assignment permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000031', 'READ_ASSIGNMENT', 'View assignments', 'DATA', 'ASSIGNMENT', NOW()),
    ('10000000-0000-0000-0000-000000000032', 'CREATE_ASSIGNMENT', 'Create assignments', 'DATA', 'ASSIGNMENT', NOW()),
    ('10000000-0000-0000-0000-000000000033', 'UPDATE_ASSIGNMENT', 'Update assignments', 'DATA', 'ASSIGNMENT', NOW()),
    ('10000000-0000-0000-0000-000000000034', 'DELETE_ASSIGNMENT', 'Delete assignments', 'DATA', 'ASSIGNMENT', NOW())
ON CONFLICT (name) DO NOTHING;

-- Group permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000041', 'READ_GROUP', 'View groups', 'DATA', 'GROUP', NOW()),
    ('10000000-0000-0000-0000-000000000042', 'CREATE_GROUP', 'Create groups', 'DATA', 'GROUP', NOW()),
    ('10000000-0000-0000-0000-000000000043', 'UPDATE_GROUP', 'Update groups', 'DATA', 'GROUP', NOW()),
    ('10000000-0000-0000-0000-000000000044', 'DELETE_GROUP', 'Delete groups', 'DATA', 'GROUP', NOW())
ON CONFLICT (name) DO NOTHING;

-- Role permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000051', 'READ_ROLE', 'View roles', 'DATA', 'ROLE', NOW()),
    ('10000000-0000-0000-0000-000000000052', 'CREATE_ROLE', 'Create roles', 'DATA', 'ROLE', NOW()),
    ('10000000-0000-0000-0000-000000000053', 'UPDATE_ROLE', 'Update roles', 'DATA', 'ROLE', NOW()),
    ('10000000-0000-0000-0000-000000000054', 'DELETE_ROLE', 'Delete roles', 'DATA', 'ROLE', NOW())
ON CONFLICT (name) DO NOTHING;

-- Permission permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000061', 'READ_PERMISSION', 'View permissions', 'DATA', 'PERMISSION', NOW()),
    ('10000000-0000-0000-0000-000000000062', 'CREATE_PERMISSION', 'Create permissions', 'DATA', 'PERMISSION', NOW()),
    ('10000000-0000-0000-0000-000000000063', 'UPDATE_PERMISSION', 'Update permissions', 'DATA', 'PERMISSION', NOW()),
    ('10000000-0000-0000-0000-000000000064', 'DELETE_PERMISSION', 'Delete permissions', 'DATA', 'PERMISSION', NOW())
ON CONFLICT (name) DO NOTHING;

-- Catalog permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000071', 'READ_CATALOG', 'View catalogs', 'DATA', 'CATALOG', NOW())
ON CONFLICT (name) DO NOTHING;

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
    'READ_USER', 'CREATE_USER', 'UPDATE_USER', 'DELETE_USER',
    'READ_DATASPACE', 'CREATE_DATASPACE', 'UPDATE_DATASPACE', 'DELETE_DATASPACE',
    'READ_DATASET', 'CREATE_DATASET', 'UPDATE_DATASET', 'DELETE_DATASET',
    'READ_ASSIGNMENT', 'CREATE_ASSIGNMENT', 'UPDATE_ASSIGNMENT', 'DELETE_ASSIGNMENT',
    'READ_GROUP', 'CREATE_GROUP', 'UPDATE_GROUP', 'DELETE_GROUP',
    'READ_ROLE', 'CREATE_ROLE', 'UPDATE_ROLE', 'DELETE_ROLE',
    'READ_PERMISSION', 'CREATE_PERMISSION', 'UPDATE_PERMISSION', 'DELETE_PERMISSION',
    'READ_CATALOG'
)
ON CONFLICT DO NOTHING;

-- DataConsumer gets READ permissions only
INSERT INTO role_permissions (role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000002', id FROM permissions
WHERE name IN (
    'READ_USER',
    'READ_DATASPACE',
    'READ_DATASET',
    'READ_ASSIGNMENT',
    'READ_GROUP',
    'READ_ROLE',
    'READ_PERMISSION',
    'READ_CATALOG'
)
ON CONFLICT DO NOTHING;

-- NoPerms gets NO permissions (empty role)

-- =============================================================================
-- USERS
-- =============================================================================
-- external_id values must match Keycloak user IDs (set by seed-keycloak-users.sh)

INSERT INTO users (id, external_id, first_name, last_name, email, active, created_at)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'fcb661c1-eb24-434e-8952-9826df11c29b', 'Authz', 'Admin', 'authz.admin@e2e.civitas.dev', true, NOW()),
    ('30000000-0000-0000-0000-000000000002', '81320fd4-3de4-41c7-8360-da7ae0ed7caa', 'Authz', 'Reader', 'authz.reader@e2e.civitas.dev', true, NOW()),
    ('30000000-0000-0000-0000-000000000003', '83029c07-282c-44ae-a675-9db7a00b1929', 'Authz', 'NoPerms', 'authz.none@e2e.civitas.dev', true, NOW())
ON CONFLICT (email) DO UPDATE SET
    external_id = EXCLUDED.external_id,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    active = EXCLUDED.active;

-- =============================================================================
-- GROUPS
-- =============================================================================

INSERT INTO groups (id, name, description, created_at)
VALUES
    ('40000000-0000-0000-0000-000000000001', 'AuthzAdminGroup', 'Group with full permissions', NOW()),
    ('40000000-0000-0000-0000-000000000002', 'AuthzReaderGroup', 'Group with read-only permissions', NOW()),
    ('40000000-0000-0000-0000-000000000003', 'AuthzNoPermsGroup', 'Group with no permissions', NOW())
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- GROUP MEMBERS
-- =============================================================================

INSERT INTO group_members (group_id, user_id)
VALUES
    ('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001'),  -- Admin user in Admin group
    ('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000002'),  -- Reader user in Reader group
    ('40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003')   -- NoPerms user in NoPerms group
ON CONFLICT DO NOTHING;

-- =============================================================================
-- ASSIGNMENTS
-- =============================================================================
-- Assign roles to groups at TENANT scope (platform-wide access)

INSERT INTO assignments (id, group_id, role_id, scope_type, scope_id, is_inherited, created_at)
VALUES
    ('50000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'TENANT', 'civitas-core', false, NOW()),  -- Admin group -> DataArchitect
    ('50000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', 'TENANT', 'civitas-core', false, NOW()),  -- Reader group -> DataConsumer
    ('50000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003', 'TENANT', 'civitas-core', false, NOW())   -- NoPerms group -> NoPerms role
ON CONFLICT ON CONSTRAINT uk_assignment_group_role_scope DO NOTHING;

-- =============================================================================
-- VERIFICATION QUERIES
-- =============================================================================

-- Verify data was inserted correctly
SELECT 'Permissions count:' AS info, COUNT(*) AS count FROM permissions WHERE name LIKE '%_USER' OR name LIKE '%_DATASET' OR name LIKE '%_DATASPACE';
SELECT 'Roles count:' AS info, COUNT(*) AS count FROM roles WHERE name LIKE 'Authz%';
SELECT 'Users count:' AS info, COUNT(*) AS count FROM users WHERE email LIKE 'authz.%';
SELECT 'Groups count:' AS info, COUNT(*) AS count FROM groups WHERE name LIKE 'Authz%';
SELECT 'Assignments count:' AS info, COUNT(*) AS count FROM assignments WHERE group_id IN (SELECT id FROM groups WHERE name LIKE 'Authz%');
