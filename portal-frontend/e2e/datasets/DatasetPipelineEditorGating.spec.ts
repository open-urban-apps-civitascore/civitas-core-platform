/**
 * E2E tests for Pipeline Editor datasource selector gating by DATASOURCE_READ.
 *
 * The pipeline editor is always accessible, but the datasource selector
 * in the DataSource node inspector is disabled without DATASOURCE_READ.
 * These tests verify the editor loads correctly for both user types.
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
import { withTestUser } from '../utils/withTestUser'

test.describe('Dataset Pipeline Editor — DATASOURCE_READ Gating', () => {
  test.setTimeout(90_000)

  let adminApi: ApiClient
  let resources: TestResources
  let dataset: { id: string; name: string }

  let userWithDatasourceRead: TestUserProfile
  let userWithoutDatasourceRead: TestUserProfile

  test.beforeAll(async () => {
    adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    resources = emptyResources()

    dataset = await adminApi.createDataset({ name: `E2E-dset-pipe-${Date.now()}` })
    resources.datasetIds.push(dataset.id)

    userWithDatasourceRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `PipeYes${Date.now()}`,
        email: `e2e-pipe-yes-${Date.now()}@e2e.civitas.dev`,
        permissions: ['DATASET_READ', 'DATASET_UPDATE', 'DATASOURCE_READ'],
        roleType: 'DATA',
        scopeType: 'TENANT',
      },
      resources,
    )

    userWithoutDatasourceRead = await createTestUserWithPermissions(
      adminApi,
      {
        firstName: 'E2E',
        lastName: `PipeNo${Date.now()}`,
        email: `e2e-pipe-no-${Date.now()}@e2e.civitas.dev`,
        permissions: ['DATASET_READ', 'DATASET_UPDATE'],
        roleType: 'DATA',
        scopeType: 'TENANT',
      },
      resources,
    )
  })

  test.afterAll(async () => {
    await cleanupTestResources(adminApi, resources)
  })

  test('Pipeline editor loads with DATASOURCE_READ', async ({ browser }) => {
    await withTestUser(browser, userWithDatasourceRead, async page => {
      await page.goto(`/datasets/${dataset.id}/data-flow`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.locator('.react-flow')).toBeVisible({ timeout: 20_000 })
    })
  })

  test('Pipeline editor loads without DATASOURCE_READ', async ({ browser }) => {
    await withTestUser(browser, userWithoutDatasourceRead, async page => {
      await page.goto(`/datasets/${dataset.id}/data-flow`)
      await page.waitForLoadState('domcontentloaded')
      await expect(page.locator('.react-flow')).toBeVisible({ timeout: 20_000 })
    })
  })
})
