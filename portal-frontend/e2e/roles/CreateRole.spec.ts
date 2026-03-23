/**
 * E2E tests for roles list & detail permission gating.
 *
 * MR320 test plan — Feature 7: Roles list, Feature 8: Role detail
 *   - "New Role" button visible/hidden based on ROLE_CREATE
 *   - Edit button visible/hidden based on ROLE_UPDATE
 *   - Permissions tab visible/hidden based on PERMISSION_READ
 *   - Group Assignment tab visible/hidden based on GROUP_READ
 *   - Delete button visible/hidden based on ROLE_DELETE
 *   - Default role: no Edit on Base Info tab, no Delete button
 *   - New role: Permissions & Group Assignment tabs disabled until saved
 */
import { expect, test } from '@playwright/test'

import { TEST_PASSWORD, TEST_USERNAME } from '../../playwright.config'
import {
  ApiClient,
  cleanupTestResources,
  createTestUserWithPermissions,
  emptyResources,
  type TestResources,
  type TestUserProfile,
} from '../../playwright/helpers/api'
import { loginAs } from '../../playwright/helpers/auth'

test.describe('Roles List — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources

  let userWithCreate: TestUserProfile
  let userReadOnly: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    // User with ROLE_READ + ROLE_CREATE → sees "Create Role" button
    userWithCreate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `RoleCreate${Date.now()}`,
        email: `e2e-rolecreate-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ROLE_CREATE'],
      },
      resources,
    )

    // User with only ROLE_READ → no create button
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `RoleRO${Date.now()}`,
        email: `e2e-rolero-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('"Create Role" button visible with ROLE_CREATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithCreate.email,
      password: userWithCreate.password,
    })

    try {
      await page.goto('/roles')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('searchArea')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByRole('button', { name: /create role|rolle erstellen/i })).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"Create Role" button hidden without ROLE_CREATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto('/roles')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('searchArea')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByRole('button', { name: /create role|rolle erstellen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})

test.describe('Role Detail — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let testRole: { id: string; name: string }

  let userWithUpdate: TestUserProfile
  let userReadOnly: TestUserProfile
  let userWithPermRead: TestUserProfile
  let userWithAssignRead: TestUserProfile
  let userWithDelete: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    testRole = await adminApi.createRole({
      name: `e2e-detail-role-${Date.now()}`,
      description: 'Role for detail permission gating',
      roleType: 'SYSTEM',
    })
    resources.roleIds.push(testRole.id)

    userWithUpdate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `RoleUpd${Date.now()}`,
        email: `e2e-roleupd-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ROLE_UPDATE'],
      },
      resources,
    )

    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `RoleDetailRO${Date.now()}`,
        email: `e2e-roledetailro-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ'],
      },
      resources,
    )

    userWithPermRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `PermRead${Date.now()}`,
        email: `e2e-permread-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'PERMISSION_READ'],
      },
      resources,
    )

    userWithAssignRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpRead${Date.now()}`,
        email: `e2e-grpread-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'GROUP_READ'],
      },
      resources,
    )

    userWithDelete = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `RoleDel${Date.now()}`,
        email: `e2e-roledel-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ROLE_UPDATE', 'ROLE_DELETE'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('Edit button visible with ROLE_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      await expect(page.getByTestId('editButton')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Edit button hidden without ROLE_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      await expect(page.getByTestId('editButton')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Permissions tab visible with PERMISSION_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithPermRead.email,
      password: userWithPermRead.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('tab-permissions')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Permissions tab hidden without PERMISSION_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('tab-basicInformation')).toBeVisible()
      await expect(page.getByTestId('tab-permissions')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Group Assignment tab visible with GROUP_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithAssignRead.email,
      password: userWithAssignRead.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('tab-groupAssignment')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Group Assignment tab hidden without GROUP_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('tab-groupAssignment')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Delete button visible with ROLE_DELETE in edit mode', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithDelete.email,
      password: userWithDelete.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // Enter edit mode
      await page.getByTestId('editButton').click()

      // Delete button should be visible on the base info tab
      await expect(page.getByRole('button', { name: /delete role|rolle löschen/i })).toBeVisible({ timeout: 10_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Delete button hidden without ROLE_DELETE in edit mode', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // Enter edit mode (has ROLE_UPDATE but not ROLE_DELETE)
      await page.getByTestId('editButton').click()

      // Delete button should NOT be visible
      await expect(page.getByRole('button', { name: /delete role|rolle löschen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})

test.describe('Default Role — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let defaultRoleId: string
  let defaultRoleName: string

  let userWithFullAccess: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    // Find a default (readonly) role — these are seeded by the system
    const rolesResponse = await adminApi.getRoles()
    const defaultRole = rolesResponse.content.find(r => r.readonly)
    if (!defaultRole) throw new Error('No default role found in system')
    defaultRoleId = defaultRole.id
    defaultRoleName = defaultRole.name

    userWithFullAccess = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `DefaultRole${Date.now()}`,
        email: `e2e-defaultrole-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ROLE_UPDATE', 'ROLE_DELETE', 'PERMISSION_READ', 'GROUP_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('default role has no Edit button on Base Info tab', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithFullAccess.email,
      password: userWithFullAccess.password,
    })

    try {
      await page.goto(`/roles/${defaultRoleId}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader').first()).toContainText(defaultRoleName, { timeout: 20_000 })

      // Default role: edit button hidden on Base Info tab (isDefaultRole && !isGroupTab)
      await expect(page.getByTestId('editButton')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('default role has no Delete button', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithFullAccess.email,
      password: userWithFullAccess.password,
    })

    try {
      await page.goto(`/roles/${defaultRoleId}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader').first()).toContainText(defaultRoleName, { timeout: 20_000 })

      // No edit button means no way to enter edit mode → no delete button either
      await expect(page.getByTestId('editButton')).not.toBeVisible()
      await expect(page.getByRole('button', { name: /delete role|rolle löschen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})

test.describe('New Role — Tab State', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let userWithCreate: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    userWithCreate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `NewRole${Date.now()}`,
        email: `e2e-newrole-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ROLE_CREATE', 'PERMISSION_READ', 'GROUP_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('Permissions and Group Assignment tabs disabled on new role creation', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithCreate.email,
      password: userWithCreate.password,
    })

    try {
      await page.goto('/roles/create')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      // Permissions tab should be visible but disabled (aria-disabled)
      const permTab = page.getByTestId('tab-permissions')
      await expect(permTab).toBeVisible({ timeout: 10_000 })
      await expect(permTab).toBeDisabled()

      // Group Assignment tab should be visible but disabled
      const groupTab = page.getByTestId('tab-groupAssignment')
      await expect(groupTab).toBeVisible()
      await expect(groupTab).toBeDisabled()
    } finally {
      await page.close()
      await context.close()
    }
  })
})
