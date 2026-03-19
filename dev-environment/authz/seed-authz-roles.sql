-- Seed authz test roles
-- Run BEFORE the backend starts so UserInitializer can find them for assignments.
-- Permissions are created by the backend's PermissionRoleInitializer.

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

SELECT 'Authz roles count:' AS info, COUNT(*) AS count FROM roles WHERE name LIKE 'Authz%';
