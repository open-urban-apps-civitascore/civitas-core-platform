/**
 * E2E tests for role detail — group assignment tab gating.
 *
 * MR320 test plan — Feature 8: Role detail, group assignment tab
 *   - "Add Groups" button visible in edit mode with ROLE_UPDATE
 *   - "Add Groups" button hidden in read-only mode without ROLE_UPDATE
 *   - Group row click navigates in read-only mode
 *   - Group row click does nothing in edit mode
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

  let userWithUpdate: TestUserProfile
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

    // User with ROLE_READ + ROLE_UPDATE + ASSIGNMENT_READ → sees "Add Groups" in edit mode
    userWithUpdate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpAssignEdit${Date.now()}`,
        email: `e2e-grpassignedit-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ROLE_UPDATE', 'ASSIGNMENT_READ'],
      },
      resources,
    )

    // User with ROLE_READ + ASSIGNMENT_READ but no ROLE_UPDATE → no "Add Groups"
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpAssignRO${Date.now()}`,
        email: `e2e-grpassignro-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ASSIGNMENT_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('"Add Groups" button visible in edit mode with ROLE_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // Enter edit mode
      await page.getByTestId('editButton').click()

      // Navigate to Group Assignment tab
      await page.getByTestId('tab-groupAssignment').click()

      // "Add Groups" button should be visible
      const addGroupsBtn = page.getByRole('button', { name: /add group|gruppe hinzufügen/i })
      await expect(addGroupsBtn).toBeVisible({ timeout: 10_000 })
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

      // "Add Groups" button should NOT be visible
      await expect(page.getByRole('button', { name: /add group|gruppe hinzufügen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})
