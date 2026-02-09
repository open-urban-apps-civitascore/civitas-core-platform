-- Dev Admin Seed Data for Team 2's Backend Dev Environment
-- Creates a dev admin user with ALL permissions so team 2 gets
-- authorization "for free" when starting their dev environment.
--
-- Dev Admin User:
--   - Email: dev.admin@civitas.dev
--   - Password: admin (set in Keycloak realm-export.json)
--   - Has ALL permissions via DevAdmin role
--
-- ID prefix A0000000-... to avoid conflicts with authz test seed (10000000-...)
--
-- Usage (runs automatically via authz-seed container):
--   psql -h postgres-portal -U admin -d portal_backend -f seed-dev-admin.sql

-- =============================================================================
-- PERMISSIONS (40 permissions: 29 original + 11 new for datasources/datastructures/release)
-- =============================================================================

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

-- Datasource permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000081', 'READ_DATASOURCE', 'View datasources', 'DATA', 'DATASOURCE', NOW()),
    ('10000000-0000-0000-0000-000000000082', 'CREATE_DATASOURCE', 'Create datasources', 'DATA', 'DATASOURCE', NOW()),
    ('10000000-0000-0000-0000-000000000083', 'UPDATE_DATASOURCE', 'Update datasources', 'DATA', 'DATASOURCE', NOW()),
    ('10000000-0000-0000-0000-000000000084', 'DELETE_DATASOURCE', 'Delete datasources', 'DATA', 'DATASOURCE', NOW())
ON CONFLICT (name) DO NOTHING;

-- Datastructure permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000091', 'READ_DATASTRUCTURE', 'View datastructures', 'DATA', 'DATASTRUCTURE', NOW()),
    ('10000000-0000-0000-0000-000000000092', 'CREATE_DATASTRUCTURE', 'Create datastructures', 'DATA', 'DATASTRUCTURE', NOW()),
    ('10000000-0000-0000-0000-000000000093', 'UPDATE_DATASTRUCTURE', 'Update datastructures', 'DATA', 'DATASTRUCTURE', NOW()),
    ('10000000-0000-0000-0000-000000000094', 'DELETE_DATASTRUCTURE', 'Delete datastructures', 'DATA', 'DATASTRUCTURE', NOW())
ON CONFLICT (name) DO NOTHING;

-- Release permissions
INSERT INTO permissions (id, name, description, permission_type, category, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000101', 'RELEASE_DATASET', 'Release datasets', 'DATA', 'DATASET', NOW()),
    ('10000000-0000-0000-0000-000000000102', 'RELEASE_DATASOURCE', 'Release datasources', 'DATA', 'DATASOURCE', NOW()),
    ('10000000-0000-0000-0000-000000000103', 'RELEASE_DATASTRUCTURE', 'Release datastructures', 'DATA', 'DATASTRUCTURE', NOW())
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- ROLE (DevAdmin with ALL permissions)
-- =============================================================================

INSERT INTO roles (id, name, description, role_type, created_at)
VALUES ('A0000000-0000-0000-0000-000000000001', 'DevAdmin', 'Dev environment admin with all permissions', 'DATA', NOW())
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- ROLE PERMISSIONS (DevAdmin gets ALL permissions)
-- =============================================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT 'A0000000-0000-0000-0000-000000000001', id FROM permissions
WHERE name IN (
    'READ_USER', 'CREATE_USER', 'UPDATE_USER', 'DELETE_USER',
    'READ_DATASPACE', 'CREATE_DATASPACE', 'UPDATE_DATASPACE', 'DELETE_DATASPACE',
    'READ_DATASET', 'CREATE_DATASET', 'UPDATE_DATASET', 'DELETE_DATASET',
    'READ_ASSIGNMENT', 'CREATE_ASSIGNMENT', 'UPDATE_ASSIGNMENT', 'DELETE_ASSIGNMENT',
    'READ_GROUP', 'CREATE_GROUP', 'UPDATE_GROUP', 'DELETE_GROUP',
    'READ_ROLE', 'CREATE_ROLE', 'UPDATE_ROLE', 'DELETE_ROLE',
    'READ_PERMISSION', 'CREATE_PERMISSION', 'UPDATE_PERMISSION', 'DELETE_PERMISSION',
    'READ_CATALOG',
    'READ_DATASOURCE', 'CREATE_DATASOURCE', 'UPDATE_DATASOURCE', 'DELETE_DATASOURCE',
    'READ_DATASTRUCTURE', 'CREATE_DATASTRUCTURE', 'UPDATE_DATASTRUCTURE', 'DELETE_DATASTRUCTURE',
    'RELEASE_DATASET', 'RELEASE_DATASOURCE', 'RELEASE_DATASTRUCTURE'
)
ON CONFLICT DO NOTHING;

-- =============================================================================
-- USER (dev.admin@civitas.dev)
-- =============================================================================
-- external_id matches the fixed UUID in Keycloak realm-export.json

INSERT INTO users (id, external_id, first_name, last_name, email, active, created_at)
VALUES ('A0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Dev', 'Admin', 'dev.admin@civitas.dev', true, NOW())
ON CONFLICT (email) DO UPDATE SET
    external_id = EXCLUDED.external_id,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    active = EXCLUDED.active;

-- =============================================================================
-- GROUP
-- =============================================================================

INSERT INTO groups (id, name, description, created_at)
VALUES ('A0000000-0000-0000-0000-000000000003', 'DevAdminGroup', 'Dev environment admin group', NOW())
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- GROUP MEMBER
-- =============================================================================

INSERT INTO group_members (group_id, user_id)
VALUES ('A0000000-0000-0000-0000-000000000003', 'A0000000-0000-0000-0000-000000000002')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- ASSIGNMENT (group -> role at TENANT scope)
-- =============================================================================

INSERT INTO assignments (id, group_id, role_id, scope_type, created_at)
VALUES ('A0000000-0000-0000-0000-000000000004', 'A0000000-0000-0000-0000-000000000003', 'A0000000-0000-0000-0000-000000000001', 'TENANT', NOW())
ON CONFLICT ON CONSTRAINT uk_assignment_group_role_scope DO NOTHING;

-- =============================================================================
-- VERIFICATION
-- =============================================================================

SELECT 'Dev admin seed complete' AS status,
       (SELECT COUNT(*) FROM permissions) AS permissions,
       (SELECT COUNT(*) FROM role_permissions WHERE role_id = 'A0000000-0000-0000-0000-000000000001') AS dev_admin_perms,
       (SELECT email FROM users WHERE id = 'A0000000-0000-0000-0000-000000000002') AS dev_admin_email;
