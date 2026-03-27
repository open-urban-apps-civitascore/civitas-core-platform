/**
 * E2E tests for Access Control tab permission gating.
 *
 * Tests that Add Group button requires GROUP_READ + ROLE_READ,
 * and is hidden without GROUP_READ.
 *
 * Uses a datastructure as the test entity (the GenericAssignmentsList
 * component is shared across all data entities).
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
import { withTestUser } from '../utils/withTestUser'

test.describe('Access Control Tab — Permission Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let ds: { id: string; name: string }

  let userWithGroupAndRoleRead: TestUserProfile
  let userWithRoleReadOnly: TestUserProfile
  let userWithNoAccessPerms: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    ds = await adminApi.createDatastructure({ name: `E2E-ds-acl-${uid()}` })
    resources.datastructureIds.push(ds.id)

    userWithGroupAndRoleRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `AclFull${uid()}`,
        email: `e2e-acl-full-${uid()}@e2e.civitas.dev`,
        permissions: ['DATASTRUCTURE_READ', 'DATASTRUCTURE_UPDATE', 'GROUP_READ', 'ROLE_READ'],
        roleType: 'DATA',
        scopeType: 'TENANT',
      },
      resources,
    )

    userWithRoleReadOnly = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `AclRole${uid()}`,
        email: `e2e-acl-role-${uid()}@e2e.civitas.dev`,
        permissions: ['DATASTRUCTURE_READ', 'DATASTRUCTURE_UPDATE', 'ROLE_READ'],
        roleType: 'DATA',
        scopeType: 'TENANT',
      },
      resources,
    )

    userWithNoAccessPerms = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `AclNone${uid()}`,
        email: `e2e-acl-none-${uid()}@e2e.civitas.dev`,
        permissions: ['DATASTRUCTURE_READ', 'DATASTRUCTURE_UPDATE'],
        roleType: 'DATA',
        scopeType: 'TENANT',
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  const navigateToAccessTab = async (page: import('@playwright/test').Page) => {
    await page.goto(`/datastructures/${ds.id}`)
    await page.waitForLoadState('domcontentloaded')
    await expect(page.getByTestId('editButton')).toBeVisible({ timeout: 20_000 })
    await page.getByTestId('editButton').click()
    await page.getByTestId('tab-accessManagement').click()
    await expect(page.getByTestId('accessManagement')).toBeVisible({ timeout: 10_000 })
  }

  test('Add Group button visible with GROUP_READ + ROLE_READ', async ({ browser }) => {
    await withTestUser(browser, userWithGroupAndRoleRead, async page => {
      await navigateToAccessTab(page)
      await expect(page.getByRole('button', { name: /add group|gruppe hinzufügen/i })).toBeVisible()
    })
  })

  test('Add Group button hidden without GROUP_READ', async ({ browser }) => {
    await withTestUser(browser, userWithRoleReadOnly, async page => {
      await navigateToAccessTab(page)
      await expect(page.getByRole('button', { name: /add group|gruppe hinzufügen/i })).not.toBeVisible()
    })
  })

  test('Add Group button hidden without GROUP_READ and ROLE_READ', async ({ browser }) => {
    await withTestUser(browser, userWithNoAccessPerms, async page => {
      await navigateToAccessTab(page)
      await expect(page.getByRole('button', { name: /add group|gruppe hinzufügen/i })).not.toBeVisible()
    })
  })
})
