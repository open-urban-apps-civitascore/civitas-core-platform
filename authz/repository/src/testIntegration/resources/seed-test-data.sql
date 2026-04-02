-- Test seed data for AuthZ Adapter integration tests

-- Create test permissions (using portal-model PermissionType enum: SYSTEM, DATA)
INSERT INTO permissions (id, name, description, permission_type, category, source, created_at)
VALUES
  ('a1111111-1111-1111-1111-111111111111', 'dataset:read', 'Read datasets', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('a2222222-2222-2222-2222-222222222222', 'dataset:write', 'Write datasets', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('a3333333-3333-3333-3333-333333333333', 'tenant:manage', 'Manage tenant', 'SYSTEM', 'TENANT_ADMINISTRATION', 'INTERNAL', NOW());

-- Create test roles (using portal-model RoleType enum: SYSTEM, DATA)
INSERT INTO roles (id, name, description, role_type, created_at)
VALUES
  ('b1111111-1111-1111-1111-111111111111', 'DataReader', 'Can read data', 'DATA', NOW()),
  ('b2222222-2222-2222-2222-222222222222', 'DataEditor', 'Can read and write data', 'DATA', NOW()),
  ('b3333333-3333-3333-3333-333333333333', 'TenantAdmin', 'Tenant administrator', 'SYSTEM', NOW());

-- Assign permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
VALUES
  ('b1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111'),
  ('b2222222-2222-2222-2222-222222222222', 'a1111111-1111-1111-1111-111111111111'),
  ('b2222222-2222-2222-2222-222222222222', 'a2222222-2222-2222-2222-222222222222'),
  ('b3333333-3333-3333-3333-333333333333', 'a3333333-3333-3333-3333-333333333333');

-- Create test groups
INSERT INTO groups (id, name, description, created_at)
VALUES
  ('c1111111-1111-1111-1111-111111111111', 'Readers Group', 'Group for data readers', NOW()),
  ('c2222222-2222-2222-2222-222222222222', 'Editors Group', 'Group for data editors', NOW());

-- Create test users
INSERT INTO users (id, external_id, email, first_name, last_name, active, created_at)
VALUES
  ('d1111111-1111-1111-1111-111111111111', 'e0000001-0000-0000-0000-000000000001', 'reader@test.com', 'Test', 'Reader', true, NOW()),
  ('d2222222-2222-2222-2222-222222222222', 'e0000002-0000-0000-0000-000000000002', 'editor@test.com', 'Test', 'Editor', true, NOW()),
  ('d3333333-3333-3333-3333-333333333333', 'e0000003-0000-0000-0000-000000000003', 'nogroups@test.com', 'No', 'Groups', true, NOW());

-- Add users to groups
INSERT INTO group_members (user_id, group_id)
VALUES
  ('d1111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111'),
  ('d2222222-2222-2222-2222-222222222222', 'c1111111-1111-1111-1111-111111111111'),
  ('d2222222-2222-2222-2222-222222222222', 'c2222222-2222-2222-2222-222222222222');

-- Create assignments (role + scope assignments for groups)
-- Note: TENANT-scoped assignments have no scope entity (getScopeId() returns null).
-- DATASPACE-scoped assignments use data_space_id FK column.
INSERT INTO assignments (id, group_id, role_id, scope_type, data_space_id, created_at)
VALUES
  ('e1111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 'b1111111-1111-1111-1111-111111111111', 'TENANT', NULL, NOW()),
  ('e2222222-2222-2222-2222-222222222222', 'c2222222-2222-2222-2222-222222222222', 'b2222222-2222-2222-2222-222222222222', 'DATASPACE', 'f2222222-2222-2222-2222-222222222222', NOW()),
  ('e3333333-3333-3333-3333-333333333333', 'c2222222-2222-2222-2222-222222222222', 'b3333333-3333-3333-3333-333333333333', 'TENANT', NULL, NOW());

-- ============================================
-- Edge case test data
-- ============================================

-- Role with no permissions (for edge case testing)
INSERT INTO roles (id, name, description, role_type, created_at)
VALUES
  ('b4444444-4444-4444-4444-444444444444', 'EmptyRole', 'Role with no permissions', 'DATA', NOW());

-- Group with no assignments (edge case)
INSERT INTO groups (id, name, description, created_at)
VALUES
  ('c3333333-3333-3333-3333-333333333333', 'Empty Assignments Group', 'Group with no role assignments', NOW());

-- User in group with no assignments
INSERT INTO users (id, external_id, email, first_name, last_name, active, created_at)
VALUES
  ('d4444444-4444-4444-4444-444444444444', 'e0000004-0000-0000-0000-000000000004', 'empty-assignments@test.com', 'Empty', 'Assignments', true, NOW());

INSERT INTO group_members (user_id, group_id)
VALUES
  ('d4444444-4444-4444-4444-444444444444', 'c3333333-3333-3333-3333-333333333333');

-- User with role that has no permissions
INSERT INTO users (id, external_id, email, first_name, last_name, active, created_at)
VALUES
  ('d5555555-5555-5555-5555-555555555555', 'e0000005-0000-0000-0000-000000000005', 'empty-perms@test.com', 'Empty', 'Permissions', true, NOW());

INSERT INTO groups (id, name, description, created_at)
VALUES
  ('c4444444-4444-4444-4444-444444444444', 'Empty Perms Group', 'Group with role that has no permissions', NOW());

INSERT INTO group_members (user_id, group_id)
VALUES
  ('d5555555-5555-5555-5555-555555555555', 'c4444444-4444-4444-4444-444444444444');

INSERT INTO assignments (id, group_id, role_id, scope_type, created_at)
VALUES
  ('e4444444-4444-4444-4444-444444444444', 'c4444444-4444-4444-4444-444444444444', 'b4444444-4444-4444-4444-444444444444', 'TENANT', NOW());

-- User with many permissions (10+) for sorting test
INSERT INTO permissions (id, name, description, permission_type, category, source, created_at)
VALUES
  ('a4444444-4444-4444-4444-444444444444', 'zebra:read', 'Zebra read', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('a5555555-5555-5555-5555-555555555555', 'alpha:write', 'Alpha write', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('a6666666-6666-6666-6666-666666666666', 'beta:delete', 'Beta delete', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('a7777777-7777-7777-7777-777777777777', 'gamma:create', 'Gamma create', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('a8888888-8888-8888-8888-888888888888', 'delta:update', 'Delta update', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('a9999999-9999-9999-9999-999999999999', 'epsilon:list', 'Epsilon list', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'theta:export', 'Theta export', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('abbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'iota:import', 'Iota import', 'DATA', 'DATA', 'INTERNAL', NOW()),
  ('accccccc-cccc-cccc-cccc-cccccccccccc', 'kappa:admin', 'Kappa admin', 'SYSTEM', 'TENANT_ADMINISTRATION', 'INTERNAL', NOW()),
  ('addddddd-dddd-dddd-dddd-dddddddddddd', 'lambda:view', 'Lambda view', 'DATA', 'DATA', 'INTERNAL', NOW());

INSERT INTO roles (id, name, description, role_type, created_at)
VALUES
  ('b5555555-5555-5555-5555-555555555555', 'ManyPermsRole', 'Role with many permissions', 'DATA', NOW());

INSERT INTO role_permissions (role_id, permission_id)
VALUES
  ('b5555555-5555-5555-5555-555555555555', 'a4444444-4444-4444-4444-444444444444'),
  ('b5555555-5555-5555-5555-555555555555', 'a5555555-5555-5555-5555-555555555555'),
  ('b5555555-5555-5555-5555-555555555555', 'a6666666-6666-6666-6666-666666666666'),
  ('b5555555-5555-5555-5555-555555555555', 'a7777777-7777-7777-7777-777777777777'),
  ('b5555555-5555-5555-5555-555555555555', 'a8888888-8888-8888-8888-888888888888'),
  ('b5555555-5555-5555-5555-555555555555', 'a9999999-9999-9999-9999-999999999999'),
  ('b5555555-5555-5555-5555-555555555555', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('b5555555-5555-5555-5555-555555555555', 'abbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),
  ('b5555555-5555-5555-5555-555555555555', 'accccccc-cccc-cccc-cccc-cccccccccccc'),
  ('b5555555-5555-5555-5555-555555555555', 'addddddd-dddd-dddd-dddd-dddddddddddd');

INSERT INTO users (id, external_id, email, first_name, last_name, active, created_at)
VALUES
  ('d6666666-6666-6666-6666-666666666666', 'e0000006-0000-0000-0000-000000000006', 'many-perms@test.com', 'Many', 'Permissions', true, NOW());

INSERT INTO groups (id, name, description, created_at)
VALUES
  ('c5555555-5555-5555-5555-555555555555', 'Many Perms Group', 'Group with role that has many permissions', NOW());

INSERT INTO group_members (user_id, group_id)
VALUES
  ('d6666666-6666-6666-6666-666666666666', 'c5555555-5555-5555-5555-555555555555');

INSERT INTO assignments (id, group_id, role_id, scope_type, created_at)
VALUES
  ('e5555555-5555-5555-5555-555555555555', 'c5555555-5555-5555-5555-555555555555', 'b5555555-5555-5555-5555-555555555555', 'TENANT', NOW());

-- User with same role at different scopes
INSERT INTO users (id, external_id, email, first_name, last_name, active, created_at)
VALUES
  ('d7777777-7777-7777-7777-777777777777', 'e0000007-0000-0000-0000-000000000007', 'multi-scope@test.com', 'Multi', 'Scope', true, NOW());

INSERT INTO groups (id, name, description, created_at)
VALUES
  ('c6666666-6666-6666-6666-666666666666', 'Multi Scope Group', 'Group with same role at different scopes', NOW());

INSERT INTO group_members (user_id, group_id)
VALUES
  ('d7777777-7777-7777-7777-777777777777', 'c6666666-6666-6666-6666-666666666666');

INSERT INTO assignments (id, group_id, role_id, scope_type, data_space_id, created_at)
VALUES
  ('e6666666-6666-6666-6666-666666666666', 'c6666666-6666-6666-6666-666666666666', 'b1111111-1111-1111-1111-111111111111', 'TENANT', NULL, NOW()),
  ('e7777777-7777-7777-7777-777777777777', 'c6666666-6666-6666-6666-666666666666', 'b1111111-1111-1111-1111-111111111111', 'DATASPACE', 'f7777777-7777-7777-7777-777777777777', NOW());
