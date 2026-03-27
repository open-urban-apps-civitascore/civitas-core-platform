/**
 * E2E tests for Datasource datastructure tab gating by DATASTRUCTURE_READ permission.
 *
 * The dataStructure tab on the datasource detail page should only be visible
 * when the user has DATASTRUCTURE_READ permission.
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

test.describe('Datasource Detail — DataStructure Tab Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let datasource: { id: string; name: string }

  let userWithDsRead: TestUserProfile
  let userWithoutDsRead: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    datasource = await adminApi.createDatasource({ name: `E2E-src-dstab-${uid()}` })
    resources.datasourceIds.push(datasource.id)

    userWithDsRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `DsTab${uid()}`,
        email: `e2e-dstab-${uid()}@e2e.civitas.dev`,
        permissions: ['DATASOURCE_READ', 'DATASOURCE_UPDATE', 'DATASTRUCTURE_READ'],
        roleType: 'DATA',
        scopeType: 'TENANT',
      },
      resources,
    )

    userWithoutDsRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `NoDsTab${uid()}`,
        email: `e2e-nodstab-${uid()}@e2e.civitas.dev`,
        permissions: ['DATASOURCE_READ', 'DATASOURCE_UPDATE'],
        roleType: 'DATA',
        scopeType: 'TENANT',
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('DataStructure tab visible with DATASTRUCTURE_READ', async ({ browser }) => {
    await withTestUser(browser, userWithDsRead, async page => {
      await page.goto(`/datasources/${datasource.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('editButton')).toBeVisible({ timeout: 20_000 })
      await expect(page.getByTestId('tab-dataStructure')).toBeVisible()
    })
  })

  test('DataStructure tab hidden without DATASTRUCTURE_READ', async ({ browser }) => {
    await withTestUser(browser, userWithoutDsRead, async page => {
      await page.goto(`/datasources/${datasource.id}`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.getByTestId('editButton')).toBeVisible({ timeout: 20_000 })
      await expect(page.getByTestId('tab-dataStructure')).not.toBeVisible()
    })
  })
})
