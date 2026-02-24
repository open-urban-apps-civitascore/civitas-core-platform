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
-- Permission naming: ENTITY_ACTION convention (e.g., USER_READ, DATASET_CREATE).
-- Must match permission strings in authz/rego/data/backends/portal_backend/data.json.
--
-- Usage (runs automatically via authz-seed container):
--   psql -h postgres-portal -U admin -d portal_backend -f seed-dev-admin.sql

-- =============================================================================
-- PERMISSIONS (33 permissions: ENTITY_ACTION naming with RELEASE operations)
-- =============================================================================

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
