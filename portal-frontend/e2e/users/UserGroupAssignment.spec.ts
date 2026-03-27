/**
 * E2E tests for user detail — groups tab permission gating.
 *
 * MR320 test plan — Feature 6: User detail, groups tab
 *   - "Add Group" button visible in edit mode with USER_UPDATE
 *   - "Add Group" button hidden without USER_UPDATE
 *   - Remove action visible in edit mode with USER_UPDATE
 *   - No remove action without USER_UPDATE
 *   - Group names are clickable links with GROUP_READ
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
  uid,
} from '../../playwright/helpers/api'
import { loginAs } from '../../playwright/helpers/auth'

test.describe('User Groups Tab — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let targetUser: { id: string }
  let testGroup: { id: string; name: string }

  let userWithUpdate: TestUserProfile
  let userReadOnly: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    // Create a target user who belongs to a group
    targetUser = await adminApi.createUser({
      firstName: 'E2EGroupsTab',
      lastName: `Target${uid()}`,
      email: `e2e-grptab-target-${uid()}@e2e.civitas.dev`,
      title: 'OTHER',
    })
    resources.userIds.push(targetUser.id)

    testGroup = await adminApi.createGroup({
      name: `e2e-grptab-group-${uid()}`,
      description: 'Group for groups tab gating tests',
      memberIds: [targetUser.id],
    })
    resources.groupIds.push(testGroup.id)

    // User with USER_READ + USER_UPDATE + GROUP_READ → sees "Add Group" and remove actions
    userWithUpdate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpTabEdit${uid()}`,
        email: `e2e-grptabedit-${uid()}@e2e.civitas.dev`,
        permissions: ['USER_READ', 'USER_UPDATE', 'GROUP_READ'],
      },
      resources,
    )

    // User with USER_READ + GROUP_READ but no USER_UPDATE → no "Add Group", no remove
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpTabRO${uid()}`,
        email: `e2e-grptabro-${uid()}@e2e.civitas.dev`,
        permissions: ['USER_READ', 'GROUP_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('"Add Group" button visible in edit mode with USER_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}?mode=edit&subtab=groups`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('tab-groups')).toBeVisible({ timeout: 20_000 })

      const addGroupButton = page.getByRole('button', { name: /add group|gruppe hinzufügen/i })
      await expect(addGroupButton).toBeVisible({ timeout: 10_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"Add Group" button hidden without USER_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}?subtab=groups`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('tab-groups')).toBeVisible({ timeout: 20_000 })

      // Wait for the group row to ensure the tab content loaded
      await expect(page.getByRole('row').filter({ hasText: testGroup.name })).toBeVisible({ timeout: 15_000 })

      // No "Add Group" button in read-only mode
      await expect(page.getByRole('button', { name: /add group|gruppe hinzufügen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('remove action visible in edit mode with USER_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}?mode=edit&subtab=groups`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('tab-groups')).toBeVisible({ timeout: 20_000 })

      // The group row should be visible
      const groupRow = page.getByRole('row').filter({ hasText: testGroup.name })
      await expect(groupRow).toBeVisible({ timeout: 15_000 })

      // In edit mode, the row should have a dropdown menu button
      await expect(groupRow.getByRole('button')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('no remove action without USER_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}?subtab=groups`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('tab-groups')).toBeVisible({ timeout: 20_000 })

      // The group row should be visible
      const groupRow = page.getByRole('row').filter({ hasText: testGroup.name })
      await expect(groupRow).toBeVisible({ timeout: 15_000 })

      // No dropdown menu button in read-only mode
      await expect(groupRow.getByRole('button')).not.toBeVisible()
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
      await page.goto(`/users/${targetUser.id}?subtab=groups`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('tab-groups')).toBeVisible({ timeout: 20_000 })

      // The group name should be a link
      await expect(page.getByRole('link', { name: testGroup.name })).toBeVisible({ timeout: 15_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })
})
