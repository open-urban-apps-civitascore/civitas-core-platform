-- Seed role-permission mappings for authz test roles
-- Run AFTER the backend starts (PermissionRoleInitializer creates the permissions).
-- Links authz test roles to the standard permissions by name.

-- DataArchitect gets ALL permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000001', id FROM permissions
WHERE name IN (
    'USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE',
    'DATASET_READ', 'DATASET_PAYLOAD_READ', 'DATASET_CREATE', 'DATASET_UPDATE', 'DATASET_DELETE', 'DATASET_RELEASE',
    'ASSIGNMENT_READ', 'ASSIGNMENT_CREATE', 'ASSIGNMENT_DELETE',
    'GROUP_READ', 'GROUP_CREATE', 'GROUP_UPDATE', 'GROUP_DELETE',
    'ROLE_READ', 'ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE',
    'PERMISSION_READ',
    'DATASOURCE_READ', 'DATASOURCE_CREATE', 'DATASOURCE_UPDATE', 'DATASOURCE_DELETE', 'DATASOURCE_RELEASE',
    'DATASTRUCTURE_READ', 'DATASTRUCTURE_CREATE', 'DATASTRUCTURE_UPDATE', 'DATASTRUCTURE_DELETE', 'DATASTRUCTURE_RELEASE',
    'INSTALLATION_READ', 'INSTALLATION_DELETE'
)
ON CONFLICT DO NOTHING;

-- DataConsumer gets READ permissions only
INSERT INTO role_permissions (role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000002', id FROM permissions
WHERE name IN (
    'USER_READ',
    'DATASET_READ',
    'DATASET_PAYLOAD_READ',
    'ASSIGNMENT_READ',
    'GROUP_READ',
    'ROLE_READ',
    'PERMISSION_READ',
    'DATASOURCE_READ',
    'DATASTRUCTURE_READ'
)
ON CONFLICT DO NOTHING;

-- NoPerms gets NO permissions (empty role)

SELECT 'Role-permissions count:' AS info, COUNT(*) AS count FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE name LIKE 'Authz%');
