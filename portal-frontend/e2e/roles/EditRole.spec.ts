/**
 * E2E tests for role detail — permissions tab gating.
 *
 * MR320 test plan — Feature 8: Role detail, permissions tab
 *   - Permission checkboxes editable in edit mode with ROLE_UPDATE
 *   - Permission checkboxes disabled in read-only mode without ROLE_UPDATE
 *   - Role template selector visible in edit mode, hidden in read-only
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

test.describe('Role Permissions Tab — Permission Gating', () => {
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
      name: `e2e-permtab-role-${Date.now()}`,
      description: 'Role for permissions tab gating tests',
      roleType: 'SYSTEM',
    })
    resources.roleIds.push(testRole.id)

    // User with ROLE_READ + ROLE_UPDATE + PERMISSION_READ → can edit checkboxes
    userWithUpdate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `PermEdit${Date.now()}`,
        email: `e2e-permedit-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'ROLE_UPDATE', 'PERMISSION_READ'],
      },
      resources,
    )

    // User with ROLE_READ + PERMISSION_READ but no ROLE_UPDATE → checkboxes disabled
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `PermRO${Date.now()}`,
        email: `e2e-permro-${Date.now()}@e2e.civitas.dev`,
        permissions: ['ROLE_READ', 'PERMISSION_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('permission checkboxes are interactive in edit mode with ROLE_UPDATE', async ({ browser }) => {
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

      // Navigate to permissions tab
      await page.getByTestId('tab-permissions').click()

      // Checkboxes should be interactive (enabled)
      const firstCheckbox = page.getByRole('checkbox').first()
      await expect(firstCheckbox).toBeVisible({ timeout: 10_000 })
      await expect(firstCheckbox).toBeEnabled()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('permission checkboxes are disabled in read-only mode without ROLE_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // No edit button should be visible (no ROLE_UPDATE)
      await expect(page.getByTestId('editButton')).not.toBeVisible()

      // Navigate to permissions tab
      await page.getByTestId('tab-permissions').click()

      // Checkboxes should be visible but disabled
      const firstCheckbox = page.getByRole('checkbox').first()
      await expect(firstCheckbox).toBeVisible({ timeout: 10_000 })
      await expect(firstCheckbox).toBeDisabled()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('role template selector visible in edit mode', async ({ browser }) => {
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

      // Navigate to permissions tab
      await page.getByTestId('tab-permissions').click()

      // Role template selector (combobox) should be visible in edit mode
      await expect(page.getByRole('combobox')).toBeVisible({ timeout: 10_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('role template selector hidden in read-only mode', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/roles/${testRole.id}?tab=SYSTEM`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testRole.name, { timeout: 20_000 })

      // Navigate to permissions tab (no edit mode available)
      await page.getByTestId('tab-permissions').click()

      // Wait for checkboxes to load (tab content rendered)
      await expect(page.getByRole('checkbox').first()).toBeVisible({ timeout: 10_000 })

      // Role template selector should NOT be visible in read-only mode
      await expect(page.getByRole('combobox')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})
