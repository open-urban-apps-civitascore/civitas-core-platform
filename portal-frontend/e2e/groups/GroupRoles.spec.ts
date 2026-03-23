/**
 * E2E tests for group detail — roles tab permission gating.
 *
 * MR320 test plan — Feature 4: Group detail, roles tab
 *   - "Add role" button visible in edit mode with GROUP_UPDATE + ROLE_READ
 *   - "Add role" button hidden without GROUP_UPDATE
 *   - "Add role" button hidden without ROLE_READ (even in edit mode)
 *   - "Add role" hidden on non-Platform scope tabs
 *   - Remove role action visible in edit mode on Platform tab
 *   - No remove role action in read-only mode
 *   - Role names are links with ROLE_READ, plain text without
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

test.describe('Group Roles Tab — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let testGroup: { id: string; name: string }
  let testRole: { id: string; name: string }

  let userWithUpdate: TestUserProfile
  let userWithUpdateNoRoleRead: TestUserProfile
  let userReadOnly: TestUserProfile
  let userWithRoleRead: TestUserProfile
  let userNoRoleRead: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    testGroup = await adminApi.createGroup({
      name: `e2e-rolestab-grp-${Date.now()}`,
      description: 'Group for roles tab gating',
    })
    resources.groupIds.push(testGroup.id)

    // Create a role and assign it to the group so the roles tab has content
    testRole = await adminApi.createRole({
      name: `e2e-rolestab-role-${Date.now()}`,
      description: 'Role for roles tab gating',
      roleType: 'SYSTEM',
    })
    resources.roleIds.push(testRole.id)

    const assignment = await adminApi.createAssignment({
      groupId: testGroup.id,
      roleId: testRole.id,
    })
    resources.assignmentIds.push(assignment.id)

    // User with GROUP_READ + GROUP_UPDATE + ROLE_READ → can enter edit mode, sees "Add role"
    userWithUpdate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpRolesEdit${Date.now()}`,
        email: `e2e-grprolesedit-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ', 'GROUP_UPDATE', 'ROLE_READ'],
      },
      resources,
    )

    // User with GROUP_READ + GROUP_UPDATE but no ROLE_READ → can edit but no "Add role"
    userWithUpdateNoRoleRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpRolesNoRole${Date.now()}`,
        email: `e2e-grprolesnorole-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ', 'GROUP_UPDATE'],
      },
      resources,
    )

    // User with GROUP_READ only → no edit button, no "Add role"
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpRolesRO${Date.now()}`,
        email: `e2e-grprolesro-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ', 'ROLE_READ'],
      },
      resources,
    )

    // User with GROUP_READ + ROLE_READ → role names are links
    userWithRoleRead = userReadOnly // same user, has ROLE_READ

    // User with GROUP_READ but no ROLE_READ → role names are plain text
    userNoRoleRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `GrpNoRoleRead${Date.now()}`,
        email: `e2e-grpnoroleread-${Date.now()}@e2e.civitas.dev`,
        permissions: ['GROUP_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('"Add role" button visible in edit mode with GROUP_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}?mode=edit`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testGroup.name, { timeout: 20_000 })

      // Navigate to Roles tab
      await page.getByTestId('tab-roles').click()

      // "Add role" button should be visible
      await expect(page.getByRole('button', { name: /add role|rolle hinzufügen/i })).toBeVisible({ timeout: 10_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"Add role" button hidden without ROLE_READ in edit mode', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdateNoRoleRead.email,
      password: userWithUpdateNoRoleRead.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}?mode=edit`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testGroup.name, { timeout: 20_000 })

      // Navigate to Roles tab
      await page.getByTestId('tab-roles').click()

      // "Add role" button should NOT be visible (has GROUP_UPDATE but not ROLE_READ)
      await expect(page.getByRole('button', { name: /add role|rolle hinzufügen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"Add role" button hidden without GROUP_UPDATE', async ({ browser }) => {
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

      // Navigate to Roles tab
      await page.getByTestId('tab-roles').click()

      // "Add role" button should NOT be visible
      await expect(page.getByRole('button', { name: /add role|rolle hinzufügen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('role names are links with ROLE_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithRoleRead.email,
      password: userWithRoleRead.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await page.getByTestId('tab-roles').click()

      // Role name should be a clickable link
      await expect(page.getByRole('link', { name: testRole.name })).toBeVisible({ timeout: 15_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('role names are plain text without ROLE_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userNoRoleRead.email,
      password: userNoRoleRead.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await page.getByTestId('tab-roles').click()

      // Role name should be visible as text but NOT as a link
      await expect(page.getByRole('cell', { name: testRole.name })).toBeVisible({ timeout: 15_000 })
      await expect(page.getByRole('link', { name: testRole.name })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('remove role action visible in edit mode on Platform tab', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}?mode=edit`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testGroup.name, { timeout: 20_000 })

      await page.getByTestId('tab-roles').click()

      // Role row should have a dropdown menu button on the Platform tab
      const roleRow = page.getByRole('row').filter({ hasText: testRole.name })
      await expect(roleRow).toBeVisible({ timeout: 15_000 })
      await expect(roleRow.getByRole('button', { name: 'Open menu' })).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('no remove role action in read-only mode', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testGroup.name, { timeout: 20_000 })

      await page.getByTestId('tab-roles').click()

      // Role row should be visible but no dropdown menu button
      const roleRow = page.getByRole('row').filter({ hasText: testRole.name })
      await expect(roleRow).toBeVisible({ timeout: 15_000 })
      await expect(roleRow.getByRole('button', { name: 'Open menu' })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"Add role" hidden on non-Platform scope tabs', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/groups/${testGroup.id}?mode=edit`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toContainText(testGroup.name, { timeout: 20_000 })

      await page.getByTestId('tab-roles').click()

      // On Platform tab, "Add role" should be visible
      await expect(page.getByRole('button', { name: /add role|rolle hinzufügen/i })).toBeVisible({ timeout: 10_000 })

      // Switch to a non-Platform scope tab (e.g. datasets)
      const datasetsTab = page.getByRole('tab', { name: /dataset/i })
      await expect(datasetsTab).toBeVisible({ timeout: 5_000 })
      await datasetsTab.click()

      // "Add role" button should NOT be visible on non-Platform tab
      await expect(page.getByRole('button', { name: /add role|rolle hinzufügen/i })).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})
