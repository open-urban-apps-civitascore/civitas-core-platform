/**
 * E2E tests for role detail — group assignment tab gating.
 *
 * MR320 test plan — Feature 8: Role detail, group assignment tab
 *   - "Add Groups" button visible in edit mode with ROLE_UPDATE + ASSIGNMENT_CREATE
 *   - "Add Groups" button hidden without ASSIGNMENT_CREATE (even in edit mode)
 *   - "Add Groups" button hidden in read-only mode without ROLE_UPDATE
 *   - Remove group assignment action visible in edit mode on Platform tab
 *   - No remove action in read-only mode
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

test.describe('Role Group Assignment Tab — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let testRole: { id: string; name: string }
  let testGroup: { id: string; name: string }

  let userWithUpdateAndCreate: TestUserProfile
  let userWithUpdateNoCreate: TestUserProfile
  let userReadOnly: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    testRole = await adminApi.createRole({
      name: `e2e-grpassign-role-${Date.now()}`,
      description: 'Role for group assignment tab gating tests',
      roleType: 'SYSTEM',
    })
    resources.roleIds.push(testRole.id)

    // Create a group and assign it to the role so the tab has content
    testGroup = await adminApi.createGroup({
      name: `e2e-grpassign-grp-${Date.now()}`,
      description: 'Group for group assignment tab gating',
    })
    resources.groupIds.push(testGroup.id)

    const assignment = await adminApi.createAssignment({
      groupId: testGroup.id,
      roleId: testRole.id,
    })
    resources.assignmentIds.push(assignment.id)

    // User with ROLE_UPDATE + ASSIGNMENT_CREATE + GROUP_READ → sees "Add Groups" in edit mode
    userWithUpdateAndCreate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpAssignEdit${Date.now()}`,
        email: `e2e-grpassignedit-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ROLE_UPDATE', 'ASSIGNMENT_CREATE', 'GROUP_READ'],
      },
      resources,
    )

    // User with ROLE_UPDATE + GROUP_READ but no ASSIGNMENT_CREATE → no "Add Groups" button
    userWithUpdateNoCreate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpAssignNoCreate${Date.now()}`,
        email: `e2e-grpassignnocreate-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ROLE_UPDATE', 'GROUP_READ'],
      },
      resources,
    )

    // User with ROLE_READ + GROUP_READ but no ROLE_UPDATE → read-only, no "Add Groups"
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpAssignRO${Date.now()}`,
        email: `e2e-grpassignro-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'GROUP_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('"Add Groups" button visible in edit mode with ASSIGNMENT_CREATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdateAndCreate.email,
      password: userWithUpdateAndCreate.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // Enter edit mode
      await page.getByTestId('editButton').click()

      // Navigate to Group Assignment tab
      await page.getByTestId('tab-groupAssignment').click()

      // "Add Groups" button should be visible on Platform (TENANT) tab
      const addGroupsBtn = page.getByRole('button', { name: /add group|gruppe hinzufügen/i })
      await expect(addGroupsBtn).toBeVisible({ timeout: 10_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"Add Groups" button hidden without ASSIGNMENT_CREATE in edit mode', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdateNoCreate.email,
      password: userWithUpdateNoCreate.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // Enter edit mode (has ROLE_UPDATE but not ASSIGNMENT_CREATE)
      await page.getByTestId('editButton').click()

      // Navigate to Group Assignment tab
      await page.getByTestId('tab-groupAssignment').click()

      // Wait for group row to load
      await expect(page.getByRole('row').filter({ hasText: testGroup.name })).toBeVisible({ timeout: 15_000 })

      // "Add Groups" button should NOT be visible
      await expect(page.getByRole('button', { name: /add group|gruppe hinzufügen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"Add Groups" button hidden in read-only mode without ROLE_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // No edit button (no ROLE_UPDATE)
      await expect(page.getByTestId('editButton')).not.toBeVisible()

      // Navigate to Group Assignment tab
      await page.getByTestId('tab-groupAssignment').click()

      // Wait for group row to load
      await expect(page.getByRole('row').filter({ hasText: testGroup.name })).toBeVisible({ timeout: 15_000 })

      // "Add Groups" button should NOT be visible
      await expect(page.getByRole('button', { name: /add group|gruppe hinzufügen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('remove group assignment visible in edit mode on Platform tab', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdateAndCreate.email,
      password: userWithUpdateAndCreate.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // Enter edit mode
      await page.getByTestId('editButton').click()

      // Navigate to Group Assignment tab
      await page.getByTestId('tab-groupAssignment').click()

      // Group row should have a dropdown menu button (remove action)
      const groupRow = page.getByRole('row').filter({ hasText: testGroup.name })
      await expect(groupRow).toBeVisible({ timeout: 15_000 })
      await expect(groupRow.getByRole('button', { name: 'Open menu' })).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('no remove action in read-only mode', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // Navigate to Group Assignment tab
      await page.getByTestId('tab-groupAssignment').click()

      // Group row should be visible but no dropdown menu button
      const groupRow = page.getByRole('row').filter({ hasText: testGroup.name })
      await expect(groupRow).toBeVisible({ timeout: 15_000 })
      await expect(groupRow.getByRole('button', { name: 'Open menu' })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})
