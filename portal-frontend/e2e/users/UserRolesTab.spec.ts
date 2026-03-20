/**
 * E2E tests for the User Detail — Roles tab.
 *
 * Covers MR320 test plan Feature 6 — Roles tab:
 *   - Roles tab visible with ASSIGNMENT_READ
 *   - Roles tab hidden without ASSIGNMENT_READ
 *   - Roles tab shows scope sub-tabs (Platform-wide, Datasets, etc.)
 *   - Role names are clickable links with ROLE_READ
 *   - Role names are plain text without ROLE_READ
 *   - Roles tab is read-only (no add/remove actions)
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

test.describe('User Detail — Roles Tab Visibility', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let targetUser: { id: string }

  // Users with different permission levels
  let userWithAssignmentRead: TestUserProfile
  let userWithoutAssignmentRead: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    // Create a target user to view
    targetUser = await adminApi.createUser({
      firstName: 'E2ERolesTab',
      lastName: `Target${Date.now()}`,
      email: `e2e-rolestab-target-${Date.now()}@e2e.civitas.dev`,
      title: 'OTHER',
    })
    resources.userIds.push(targetUser.id)

    // User with USER_READ + ASSIGNMENT_READ → can see the Roles tab
    userWithAssignmentRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `AssignRead${Date.now()}`,
        email: `e2e-assignread-${Date.now()}@e2e.civitas.dev`,
        permissions: ['USER_READ', 'ASSIGNMENT_READ'],
      },
      resources,
    )

    // User with only USER_READ → cannot see the Roles tab
    userWithoutAssignmentRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `NoAssign${Date.now()}`,
        email: `e2e-noassign-${Date.now()}@e2e.civitas.dev`,
        permissions: ['USER_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('Roles tab visible with ASSIGNMENT_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithAssignmentRead.email,
      password: userWithAssignmentRead.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}`)
      await page.waitForLoadState('domcontentloaded')

      // Wait for the page to load
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      // The "Roles" tab should be visible
      await expect(page.getByTestId('tab-roles')).toBeVisible({ timeout: 10_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Roles tab hidden without ASSIGNMENT_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithoutAssignmentRead.email,
      password: userWithoutAssignmentRead.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}`)
      await page.waitForLoadState('domcontentloaded')

      // Wait for the page to load
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      // The "User Data" tab should be visible (always shown)
      await expect(page.getByTestId('tab-userData')).toBeVisible({ timeout: 10_000 })

      // The "Roles" tab should NOT be visible
      await expect(page.getByTestId('tab-roles')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})

test.describe('User Detail — Roles Tab Content', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let targetUser: { id: string }
  let testRole: { id: string; name: string }
  let testGroup: { id: string }

  // Users with different permission levels
  let userWithRoleRead: TestUserProfile
  let userWithoutRoleRead: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    // Create a target user
    targetUser = await adminApi.createUser({
      firstName: 'E2ERolesContent',
      lastName: `Target${Date.now()}`,
      email: `e2e-rolescontent-target-${Date.now()}@e2e.civitas.dev`,
      title: 'OTHER',
    })
    resources.userIds.push(targetUser.id)

    // Create a custom role with a unique name
    testRole = await adminApi.createRole({
      name: `e2e-roletab-role-${Date.now()}`,
      description: 'E2E test role for roles tab',
      roleType: 'SYSTEM',
    })
    resources.roleIds.push(testRole.id)

    // Create a group to link the user and role
    testGroup = await adminApi.createGroup({
      name: `e2e-roletab-group-${Date.now()}`,
      description: 'E2E test group for roles tab',
      memberIds: [targetUser.id],
    })
    resources.groupIds.push(testGroup.id)

    // Create an assignment: group + role + TENANT scope
    const assignment = await adminApi.createAssignment({
      groupId: testGroup.id,
      roleId: testRole.id,
      scopeType: 'TENANT',
    })
    resources.assignmentIds.push(assignment.id)

    // User with USER_READ + ASSIGNMENT_READ + ROLE_READ → role name is a link
    userWithRoleRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `RoleRead${Date.now()}`,
        email: `e2e-roleread-${Date.now()}@e2e.civitas.dev`,
        permissions: ['USER_READ', 'ASSIGNMENT_READ', 'ROLE_READ'],
      },
      resources,
    )

    // User with USER_READ + ASSIGNMENT_READ but no ROLE_READ → role name is plain text
    userWithoutRoleRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `NoRoleRead${Date.now()}`,
        email: `e2e-noroleread-${Date.now()}@e2e.civitas.dev`,
        permissions: ['USER_READ', 'ASSIGNMENT_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('Roles tab shows scope sub-tabs', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithRoleRead.email,
      password: userWithRoleRead.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}?subtab=roles`)
      await page.waitForLoadState('domcontentloaded')

      await expect(page.getByTestId('tab-roles')).toBeVisible({ timeout: 20_000 })

      // Verify scope sub-tabs are present
      await expect(page.getByTestId('tab-platformWide')).toBeVisible({ timeout: 10_000 })
      await expect(page.getByTestId('tab-dataset')).toBeVisible()
      await expect(page.getByTestId('tab-datasource')).toBeVisible()
      await expect(page.getByTestId('tab-datastructure')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('role names are clickable links with ROLE_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithRoleRead.email,
      password: userWithRoleRead.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}?subtab=roles`)
      await page.waitForLoadState('domcontentloaded')

      await expect(page.getByTestId('tab-roles')).toBeVisible({ timeout: 20_000 })

      // On the Platform-wide sub-tab (default), the assigned role should be visible as a link
      const roleLink = page.getByRole('link', { name: testRole.name })
      await expect(roleLink).toBeVisible({ timeout: 15_000 })
      await expect(roleLink).toHaveAttribute('href', `/roles/${testRole.id}`)
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('role names are plain text without ROLE_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithoutRoleRead.email,
      password: userWithoutRoleRead.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}?subtab=roles`)
      await page.waitForLoadState('domcontentloaded')

      await expect(page.getByTestId('tab-roles')).toBeVisible({ timeout: 20_000 })

      // The role name should be visible as text but NOT as a link
      await expect(page.getByRole('cell', { name: testRole.name })).toBeVisible({ timeout: 15_000 })
      await expect(page.getByRole('link', { name: testRole.name })).toHaveCount(0)
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Roles tab is read-only — no add or remove buttons', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithRoleRead.email,
      password: userWithRoleRead.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}?subtab=roles`)
      await page.waitForLoadState('domcontentloaded')

      await expect(page.getByTestId('tab-roles')).toBeVisible({ timeout: 20_000 })

      // Wait for table content to load
      await expect(page.getByRole('cell', { name: testRole.name })).toBeVisible({ timeout: 15_000 })

      // No add/assign buttons should exist on this tab
      await expect(page.getByRole('button', { name: /add|assign|hinzufügen|zuweisen/i })).not.toBeVisible()

      // No remove/delete actions in the table rows
      const roleRow = page.getByRole('row').filter({ hasText: testRole.name })
      await expect(roleRow.getByRole('button')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})
