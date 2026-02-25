-- Dev Admin Seed Data for Backend Dev Environment
-- Grants the dev user ALL permissions so developers get authorization "for free"
-- when starting the dev environment with full authz mode.
--
-- Dev Admin User:
--   - Email: dev@civitas.local
--   - Password: dev123 (set in Keycloak realm-export.json)
--   - Has ALL permissions via DevAdmin role + DevAdminGroup
--
-- IMPORTANT: This seed does NOT create permissions or the user — those are created
-- by portal-backend's PermissionRoleInitializer and LocalUserInitializer at startup.
-- This seed only creates the DevAdmin role, group, assignment, and permission links.
-- The start script runs this AFTER the backend is healthy so those exist.
--
-- Usage (runs automatically via start-portal-dev.sh after backend starts):
--   psql -h postgres-portal -U admin -d portal_backend -f seed-dev-admin.sql

-- =============================================================================
-- ROLE (DevAdmin with ALL permissions)
-- =============================================================================

INSERT INTO roles (id, name, description, role_type, created_at)
VALUES ('A0000000-0000-0000-0000-000000000001', 'DevAdmin', 'Dev environment admin with all permissions', 'DATA', NOW())
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- GROUP
-- =============================================================================

INSERT INTO groups (id, name, description, created_at)
VALUES ('A0000000-0000-0000-0000-000000000003', 'DevAdminGroup', 'Dev environment admin group', NOW())
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- GROUP MEMBER (look up user by email — ID may vary depending on who created it)
-- =============================================================================

INSERT INTO group_members (group_id, user_id)
SELECT 'A0000000-0000-0000-0000-000000000003', id FROM users
WHERE email = 'dev@civitas.local'
ON CONFLICT DO NOTHING;

-- =============================================================================
-- ASSIGNMENT (group -> role at TENANT scope)
-- =============================================================================

INSERT INTO assignments (id, group_id, role_id, scope_type, created_at)
VALUES ('A0000000-0000-0000-0000-000000000004', 'A0000000-0000-0000-0000-000000000003', 'A0000000-0000-0000-0000-000000000001', 'TENANT', NOW())
ON CONFLICT ON CONSTRAINT uk_assignment_group_role_scope DO NOTHING;

-- =============================================================================
-- ROLE PERMISSIONS (DevAdmin gets ALL permissions)
-- =============================================================================
-- Links to permissions created by portal-backend's PermissionRoleInitializer.
-- Uses SELECT by name so it works regardless of the UUID assigned to each permission.

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

-- NOTE: Dataspace and catalog endpoints are null-permission (GET only, any authenticated user).
-- No database permissions needed for these — OPA allows them without checking permissions.

-- =============================================================================
-- VERIFICATION
-- =============================================================================

SELECT 'Dev admin seed complete' AS status,
       (SELECT COUNT(*) FROM permissions) AS permissions,
       (SELECT COUNT(*) FROM role_permissions WHERE role_id = 'A0000000-0000-0000-0000-000000000001') AS dev_admin_perms,
       (SELECT email FROM users WHERE email = 'dev@civitas.local') AS dev_admin_email;
