/**
 * E2E tests for group list & detail permission gating.
 *
 * MR320 test plan — Feature 3: Groups list, Feature 4: Group detail
 *   - "New Group" button visible/hidden based on GROUP_CREATE
 *   - Group names are links vs plain text based on GROUP_READ
 *   - Delete action visible/hidden based on GROUP_DELETE
 *   - Edit button visible/hidden based on GROUP_UPDATE
 *   - Users tab visible/hidden based on USER_READ
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

test.describe('Groups List — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let testGroup: { id: string; name: string }

  let userWithCreate: TestUserProfile
  let userReadOnly: TestUserProfile
  let userWithDelete: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    // Create a group so the list is not empty
    testGroup = await adminApi.createGroup({
      name: `e2e-gating-grp-${Date.now()}`,
      description: 'Group for permission gating tests',
    })
    resources.groupIds.push(testGroup.id)

    // User with GROUP_READ + GROUP_CREATE → sees New Group button
    userWithCreate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpCreate${Date.now()}`,
        email: `e2e-grpcreate-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ', 'GROUP_CREATE'],
      },
      resources,
    )

    // User with only GROUP_READ → no New Group button, names are links
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpReadOnly${Date.now()}`,
        email: `e2e-grpreadonly-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ'],
      },
      resources,
    )

    // User with GROUP_READ + GROUP_DELETE → sees delete action in row menu
    userWithDelete = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpDelete${Date.now()}`,
        email: `e2e-grpdelete-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ', 'GROUP_DELETE'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('"New Group" button visible with GROUP_CREATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithCreate.email,
      password: userWithCreate.password,
    })

    try {
      await page.goto('/groups')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('searchArea')).toBeVisible({ timeout: 20_000 })

      // The create button should be visible
      await expect(page.getByRole('button', { name: /new group|neue gruppe/i })).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"New Group" button hidden without GROUP_CREATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto('/groups')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('searchArea')).toBeVisible({ timeout: 20_000 })

      // The create button should NOT be visible
      await expect(page.getByRole('button', { name: /new group|neue gruppe/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('group names are clickable links with GROUP_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto('/groups')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('searchArea')).toBeVisible({ timeout: 20_000 })

      // Search for our test group
      await page.getByTestId('searchArea').locator('input').fill(testGroup.name)
      await expect(page.getByRole('row').filter({ hasText: testGroup.name })).toBeVisible({ timeout: 10_000 })

      // The group name should be rendered as a link
      await expect(page.getByRole('link', { name: testGroup.name })).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('delete action visible with GROUP_DELETE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithDelete.email,
      password: userWithDelete.password,
    })

    try {
      await page.goto('/groups')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('searchArea')).toBeVisible({ timeout: 20_000 })

      await page.getByTestId('searchArea').locator('input').fill(testGroup.name)
      const row = page.getByRole('row').filter({ hasText: testGroup.name })
      await expect(row).toBeVisible({ timeout: 10_000 })

      // The row should have an "Open menu" button (actions column present)
      await expect(row.getByRole('button', { name: 'Open menu' })).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('no actions menu without GROUP_DELETE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto('/groups')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('searchArea')).toBeVisible({ timeout: 20_000 })

      await page.getByTestId('searchArea').locator('input').fill(testGroup.name)
      const row = page.getByRole('row').filter({ hasText: testGroup.name })
      await expect(row).toBeVisible({ timeout: 10_000 })

      // No "Open menu" button — actions column is suppressed
      await expect(row.getByRole('button', { name: 'Open menu' })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})

test.describe('Group Detail — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let testGroup: { id: string; name: string }

  let userWithUpdate: TestUserProfile
  let userReadOnly: TestUserProfile
  let userWithUserRead: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    testGroup = await adminApi.createGroup({
      name: `e2e-detail-grp-${Date.now()}`,
      description: 'Group for detail permission gating',
    })
    resources.groupIds.push(testGroup.id)

    // User with GROUP_READ + GROUP_UPDATE → sees Edit button
    userWithUpdate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpUpdate${Date.now()}`,
        email: `e2e-grpupdate-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ', 'GROUP_UPDATE'],
      },
      resources,
    )

    // User with only GROUP_READ → no Edit button
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpRO${Date.now()}`,
        email: `e2e-grpro-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ'],
      },
      resources,
    )

    // User with GROUP_READ + USER_READ → sees Users tab
    userWithUserRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpUserRead${Date.now()}`,
        email: `e2e-grpuserread-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ', 'USER_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('Edit button visible with GROUP_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('editButton')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Edit button hidden without GROUP_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('editButton')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Users tab visible with USER_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUserRead.email,
      password: userWithUserRead.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('tab-users')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Users tab hidden without USER_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      // Info and Roles tabs should be visible
      await expect(page.getByTestId('tab-info')).toBeVisible()
      await expect(page.getByTestId('tab-roles')).toBeVisible()

      // Users tab should NOT be visible
      await expect(page.getByTestId('tab-users')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})
