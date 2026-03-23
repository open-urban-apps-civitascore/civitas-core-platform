/**
 * E2E tests for group detail — users tab permission gating.
 *
 * MR320 test plan — Feature 4: Group detail, users tab
 *   - "Assign User" button visible in edit mode with GROUP_UPDATE + USER_READ
 *   - "Assign User" button hidden without GROUP_UPDATE
 *   - User names are clickable links with USER_READ
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

test.describe('Group Users Tab — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let testGroup: { id: string; name: string }
  let memberUser: { id: string; firstName: string; lastName: string }

  let userWithUpdate: TestUserProfile
  let userReadOnly: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    // Create a member user
    memberUser = await adminApi.createUser({
      firstName: 'E2EMember',
      lastName: `User${Date.now()}`,
      email: `e2e-member-${Date.now()}@e2e.civitas.dev`,
      title: 'OTHER',
    })
    resources.userIds.push(memberUser.id)

    // Create a group with the member
    testGroup = await adminApi.createGroup({
      name: `e2e-userstab-grp-${Date.now()}`,
      description: 'Group for users tab gating',
      memberIds: [memberUser.id],
    })
    resources.groupIds.push(testGroup.id)

    // User with GROUP_READ + GROUP_UPDATE + USER_READ → sees "Assign User"
    userWithUpdate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpUsersEdit${Date.now()}`,
        email: `e2e-grpusersedit-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ', 'GROUP_UPDATE', 'USER_READ'],
      },
      resources,
    )

    // User with GROUP_READ + USER_READ but no GROUP_UPDATE → no "Assign User", no edit button
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpUsersRO${Date.now()}`,
        email: `e2e-grpusersro-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ', 'USER_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('"Assign User" button visible in edit mode with GROUP_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}?mode=edit`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testGroup.name, { timeout: 20_000 })

      // Navigate to Users tab
      await page.getByTestId('tab-users').click()

      // "Assign User" button should be visible
      await expect(page.getByRole('button', { name: /assign user|benutzer zuweisen/i })).toBeVisible({
        timeout: 10_000,
      })
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"Assign User" button hidden without GROUP_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testGroup.name, { timeout: 20_000 })

      // No edit button (no GROUP_UPDATE)
      await expect(page.getByTestId('editButton')).not.toBeVisible()

      // Navigate to Users tab
      await page.getByTestId('tab-users').click()

      // Wait for member to appear (tab content loaded)
      const memberName = `${memberUser.firstName} ${memberUser.lastName}`
      await expect(page.getByRole('row').filter({ hasText: memberName })).toBeVisible({ timeout: 15_000 })

      // "Assign User" button should NOT be visible
      await expect(page.getByRole('button', { name: /assign user|benutzer zuweisen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('user names are clickable links with USER_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await page.getByTestId('tab-users').click()

      // Member name should be a clickable link
      const memberName = `${memberUser.firstName} ${memberUser.lastName}`
      await expect(page.getByRole('link', { name: memberName })).toBeVisible({ timeout: 15_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })
})
