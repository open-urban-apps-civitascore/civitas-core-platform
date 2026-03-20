/**
 * E2E tests for users list & detail permission gating.
 *
 * MR320 test plan — Feature 5: Users list, Feature 6: User detail
 *   - "Add User" button visible/hidden based on USER_CREATE
 *   - User names are links vs plain text based on USER_READ
 *   - Edit button visible/hidden based on USER_UPDATE
 *   - Groups tab visible/hidden based on GROUP_READ
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

test.describe('Users List — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources

  let userWithCreate: TestUserProfile
  let userReadOnly: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    // User with USER_READ + USER_CREATE → sees "Add User" button
    userWithCreate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `UserCreate${Date.now()}`,
        email: `e2e-usercreate-${Date.now()}@e2e.civitas.dev`,
        permissions: ['USER_READ', 'USER_CREATE'],
      },
      resources,
    )

    // User with only USER_READ → no "Add User" button, names are links
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `UserRO${Date.now()}`,
        email: `e2e-userro-${Date.now()}@e2e.civitas.dev`,
        permissions: ['USER_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('"Add User" button visible with USER_CREATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithCreate.email,
      password: userWithCreate.password,
    })

    try {
      await page.goto('/users')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('usersTable')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('addUserButton')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('"Add User" button hidden without USER_CREATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto('/users')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('usersTable')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('addUserButton')).not.toBeVisible()
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
      await page.goto('/users')
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('usersTable')).toBeVisible({ timeout: 20_000 })

      // At least one user row should have a link (the test users we created)
      const firstUserLink = page.getByTestId('usersTable').getByRole('link').first()
      await expect(firstUserLink).toBeVisible({ timeout: 10_000 })
    } finally {
      await page.close()
      await context.close()
    }
  })
})

test.describe('User Detail — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let targetUser: { id: string }

  let userWithUpdate: TestUserProfile
  let userReadOnly: TestUserProfile
  let userWithGroupRead: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    targetUser = await adminApi.createUser({
      firstName: 'E2ETarget',
      lastName: `Detail${Date.now()}`,
      email: `e2e-target-detail-${Date.now()}@e2e.civitas.dev`,
      title: 'OTHER',
    })
    resources.userIds.push(targetUser.id)

    // User with USER_READ + USER_UPDATE → sees Edit button
    userWithUpdate = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `UserUpd${Date.now()}`,
        email: `e2e-userupd-${Date.now()}@e2e.civitas.dev`,
        permissions: ['USER_READ', 'USER_UPDATE'],
      },
      resources,
    )

    // User with only USER_READ → no Edit button
    userReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `UserDetailRO${Date.now()}`,
        email: `e2e-userdetailro-${Date.now()}@e2e.civitas.dev`,
        permissions: ['USER_READ'],
      },
      resources,
    )

    // User with USER_READ + GROUP_READ → sees Groups tab
    userWithGroupRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `UserGrpRead${Date.now()}`,
        email: `e2e-usergrpread-${Date.now()}@e2e.civitas.dev`,
        permissions: ['USER_READ', 'GROUP_READ'],
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('Edit button visible with USER_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithUpdate.email,
      password: userWithUpdate.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('editButton')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Edit button hidden without USER_UPDATE', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('editButton')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Groups tab visible with GROUP_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userWithGroupRead.email,
      password: userWithGroupRead.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('tab-groups')).toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })

  test('Groups tab hidden without GROUP_READ', async ({ browser }) => {
    const { page, context } = await loginAs(browser, {
      email: userReadOnly.email,
      password: userReadOnly.password,
    })

    try {
      await page.goto(`/users/${targetUser.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('pageHeader')).toBeVisible({ timeout: 20_000 })

      await expect(page.getByTestId('tab-userData')).toBeVisible()
      await expect(page.getByTestId('tab-groups')).not.toBeVisible()
    } finally {
      await page.close()
      await context.close()
    }
  })
})
