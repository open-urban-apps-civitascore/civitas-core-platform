/**
 * E2E test setup helpers.
 *
 * Provides functions to create test users with specific permission profiles.
 * Each test user gets:
 *   1. A portal-backend user record (via API)
 *   2. A Keycloak account (so they can log in)
 *   3. A group + role + assignment with the specified permissions
 *
 * All created resources are tracked for cleanup in teardown.
 */
import { ApiClient } from './apiClient'
import { KeycloakClient } from './keycloakClient'

const E2E_TEST_PASSWORD = process.env.KC_TEST_PASSWORD ?? 'e2eTestPass1!'

/** Short random ID for unique test data names. Collision-safe across parallel workers. */
export const uid = () => crypto.randomUUID().slice(0, 8)

/** Tracks all resources created during a test for cleanup. */
export type TestResources = {
  userIds: string[]
  groupIds: string[]
  roleIds: string[]
  assignmentIds: string[]
  keycloakEmails: string[]
  datasetIds: string[]
  datasourceIds: string[]
  datastructureIds: string[]
}

const emptyResources = (): TestResources => ({
  userIds: [],
  groupIds: [],
  roleIds: [],
  assignmentIds: [],
  keycloakEmails: [],
  datasetIds: [],
  datasourceIds: [],
  datastructureIds: [],
})

export type TestUserProfile = {
  /** Portal user ID */
  userId: string
  /** Email (used as username for Keycloak login) */
  email: string
  /** Password for Keycloak login */
  password: string
  firstName: string
  lastName: string
}

/**
 * Creates a fully provisioned test user with specific permissions.
 *
 * @param adminApi - ApiClient authenticated as an admin user
 * @param opts - User details and desired permissions
 * @param resources - Resource tracker for cleanup
 * @returns The created user profile (credentials for login)
 */
export const createTestUserWithPermissions = async (
  adminApi: ApiClient,
  opts: {
    firstName: string
    lastName: string
    email: string
    /** Permission names to grant (e.g., ['USER_CREATE', 'USER_READ']) */
    permissions: string[]
    /** Scope type for the assignment (default: TENANT) */
    scopeType?: string
    /** Scope ID (null for TENANT scope) */
    scopeId?: string
    /** Role type (default: SYSTEM). Use DATA for resource-scoped assignments. */
    roleType?: 'SYSTEM' | 'DATA'
  },
  resources: TestResources,
): Promise<TestUserProfile> => {
  // 1. Find permission IDs by name
  const allPerms = await adminApi.getPermissions()
  const permissionIds = opts.permissions.map(name => {
    const perm = allPerms.find(p => p.name === name)
    if (!perm) throw new Error(`Permission not found: ${name}`)
    return perm.id
  })

  // 2. Create a role with exactly these permissions
  const roleName = `e2e-role-${uid()}`
  const role = await adminApi.createRole({
    name: roleName,
    description: `E2E test role for ${opts.email}`,
    roleType: opts.roleType ?? 'SYSTEM',
    permissionIds,
  })
  resources.roleIds.push(role.id)

  // 3. Create the user in the portal backend
  const user = await adminApi.createUser({
    firstName: opts.firstName,
    lastName: opts.lastName,
    email: opts.email,
    title: 'OTHER',
  })
  resources.userIds.push(user.id)

  // 4. Create a group and add the user as member
  const groupName = `e2e-group-${uid()}`
  const group = await adminApi.createGroup({
    name: groupName,
    description: `E2E test group for ${opts.email}`,
    memberIds: [user.id],
  })
  resources.groupIds.push(group.id)

  // 5. Create an assignment: group + role + scope
  const assignment = await adminApi.createAssignment({
    groupId: group.id,
    roleId: role.id,
    ...(opts.scopeType ? { scopeType: opts.scopeType } : {}),
    ...(opts.scopeId ? { scopeId: opts.scopeId } : {}),
  })
  resources.assignmentIds.push(assignment.id)

  // 6. Create the user in Keycloak so they can log in
  const kc = await KeycloakClient.create()
  await kc.createUser({
    email: opts.email,
    firstName: opts.firstName,
    lastName: opts.lastName,
    password: E2E_TEST_PASSWORD,
  })
  resources.keycloakEmails.push(opts.email)

  return {
    userId: user.id,
    email: opts.email,
    password: E2E_TEST_PASSWORD,
    firstName: opts.firstName,
    lastName: opts.lastName,
  }
}

/**
 * Clean up all resources created during a test.
 * Deletes in reverse dependency order: assignments → groups → roles → users → keycloak users.
 */
export const cleanupTestResources = async (adminApi: ApiClient, resources: TestResources): Promise<void> => {
  // Delete datasets first (they may reference other entities)
  for (const id of resources.datasetIds) {
    try {
      await adminApi.deleteDataset(id)
    } catch (e) {
      console.warn(`Cleanup: failed to delete dataset ${id}:`, e)
    }
  }

  // Delete datasources
  for (const id of resources.datasourceIds) {
    try {
      await adminApi.deleteDatasource(id)
    } catch (e) {
      console.warn(`Cleanup: failed to delete datasource ${id}:`, e)
    }
  }

  // Delete datastructures
  for (const id of resources.datastructureIds) {
    try {
      await adminApi.deleteDatastructure(id)
    } catch (e) {
      console.warn(`Cleanup: failed to delete datastructure ${id}:`, e)
    }
  }

  // Delete assignments first
  for (const id of resources.assignmentIds) {
    try {
      await adminApi.deleteAssignment(id)
    } catch (e) {
      console.warn(`Cleanup: failed to delete assignment ${id}:`, e)
    }
  }

  // Delete groups (removes membership)
  for (const id of resources.groupIds) {
    try {
      await adminApi.deleteGroup(id)
    } catch (e) {
      console.warn(`Cleanup: failed to delete group ${id}:`, e)
    }
  }

  // Delete roles
  for (const id of resources.roleIds) {
    try {
      await adminApi.deleteRole(id)
    } catch (e) {
      console.warn(`Cleanup: failed to delete role ${id}:`, e)
    }
  }

  // Delete portal users
  for (const id of resources.userIds) {
    try {
      await adminApi.deleteUser(id)
    } catch (e) {
      console.warn(`Cleanup: failed to delete user ${id}:`, e)
    }
  }

  // Delete Keycloak users
  const kc = await KeycloakClient.create()
  for (const email of resources.keycloakEmails) {
    try {
      await kc.deleteUserByEmail(email)
    } catch (e) {
      console.warn(`Cleanup: failed to delete KC user ${email}:`, e)
    }
  }
}

/**
 * Pre-defined permission profiles for common test scenarios.
 */
export const PERMISSION_PROFILES = {
  /** Full tenant admin — all permissions */
  tenantAdmin: [
    'USER_CREATE',
    'USER_READ',
    'USER_UPDATE',
    'USER_DELETE',
    'ROLE_CREATE',
    'ROLE_READ',
    'ROLE_UPDATE',
    'ROLE_DELETE',
    'ASSIGNMENT_CREATE',
    'ASSIGNMENT_READ',
    'ASSIGNMENT_DELETE',
    'GROUP_CREATE',
    'GROUP_READ',
    'GROUP_UPDATE',
    'GROUP_DELETE',
    'PERMISSION_READ',
    'DATASET_CREATE',
    'DATASET_READ',
    'DATASET_UPDATE',
    'DATASET_DELETE',
    'DATASET_RELEASE',
    'DATASOURCE_CREATE',
    'DATASOURCE_READ',
    'DATASOURCE_UPDATE',
    'DATASOURCE_DELETE',
    'DATASOURCE_RELEASE',
    'DATASTRUCTURE_CREATE',
    'DATASTRUCTURE_READ',
    'DATASTRUCTURE_UPDATE',
    'DATASTRUCTURE_DELETE',
    'DATASTRUCTURE_RELEASE',
  ],

  /** Can create users but not update/delete */
  userCreateOnly: ['USER_CREATE', 'USER_READ'],

  /** Can read and update users, plus read groups (for group tab) */
  userUpdateWithGroups: ['USER_READ', 'USER_UPDATE', 'GROUP_READ'],

  /** Can read and update users, but cannot read groups */
  userUpdateNoGroups: ['USER_READ', 'USER_UPDATE'],

  /** Can only read users — no create/update/delete */
  userReadOnly: ['USER_READ'],

  /** No user permissions at all — only dataset read (to have at least something) */
  noUserPermissions: ['DATASET_READ'],

  /** Dataset read + update (for scoped permission tests) */
  datasetReadUpdate: ['DATASET_READ', 'DATASET_UPDATE'],

  /** Datasource read + update (for scoped permission tests) */
  datasourceReadUpdate: ['DATASOURCE_READ', 'DATASOURCE_UPDATE'],

  /** Datastructure read + update (for scoped permission tests) */
  datastructureReadUpdate: ['DATASTRUCTURE_READ', 'DATASTRUCTURE_UPDATE'],

  /** Datastructure full CRUD */
  datastructureAdmin: [
    'DATASTRUCTURE_CREATE',
    'DATASTRUCTURE_READ',
    'DATASTRUCTURE_UPDATE',
    'DATASTRUCTURE_DELETE',
    'DATASTRUCTURE_RELEASE',
  ],

  /** Datastructure read only */
  datastructureReadOnly: ['DATASTRUCTURE_READ'],
} as const

export { emptyResources }
