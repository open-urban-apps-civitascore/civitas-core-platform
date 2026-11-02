import { expect, test } from '@playwright/test'

import { createTestDataset, TestDataset } from '../../playwright/helpers/dataset/createTestDataset'
import { removeTestDataset } from '../../playwright/helpers/dataset/removeTestDataset'

test.describe('Dataset List', async () => {
  let dataset: TestDataset

  test.beforeEach(async ({ page }) => {
    dataset = await createTestDataset()
    await page.goto('/datasets')
    await page.waitForLoadState('networkidle')
  })

  test.afterEach(async () => {
    await removeTestDataset(dataset.id)
  })

  test('renders the page elements', async ({ page }) => {
    await page.getByTestId('loadingSkeleton').waitFor({ state: 'hidden' })

    const pageHeader = page.getByTestId('pageHeader')
    await expect(pageHeader).toBeVisible()
    await expect(pageHeader).toContainText('Datasets')
    await expect(page.getByTestId('addDatasetButton')).toBeVisible()

    await expect(page.getByTestId('datasetsTable')).toBeVisible()

    const searchArea = page.getByTestId('searchArea')
    await expect(searchArea).toBeVisible()
    await searchArea.locator('input').fill(dataset.name)

    const rows = page.getByRole('row')
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(1)).toContainText(dataset.name)

    await searchArea.locator('input').fill(crypto.randomUUID())
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(1)).toContainText('No results found.')
  })

  test('navigates to create dataset overview page', async ({ page }) => {
    await page.getByTestId('addDatasetButton').click()
    await expect(page.getByTestId('createDatasetPage')).toBeVisible()
  })

  test('navigates to dataset overview page when clicking on dataset name', async ({ page }) => {
    await page.getByRole('table').waitFor({ state: 'visible' })
    await page.getByTestId('searchArea').locator('input').fill(dataset.name)

    const datasetRow = page.getByRole('row').filter({ hasText: dataset.name })
    await datasetRow.waitFor({ state: 'visible' })
    await datasetRow.getByRole('cell', { name: dataset.name }).click()
    await expect(page.getByTestId('datasetPage')).toBeVisible()
    await expect(page.getByTestId('pageHeader')).toContainText(dataset.name)
  })

  test('navigates to user details when clicking on the creator', async ({ page }) => {
    await page.getByRole('table').waitFor({ state: 'visible' })
    await page.getByTestId('searchArea').locator('input').fill(dataset.name)

    const datasetRow = page.getByRole('row').filter({ hasText: dataset.name })
    await datasetRow.waitFor({ state: 'visible' })
    await datasetRow.locator('a[href^="/users/"]').click()
    await expect(page).toHaveURL(/\/users\/[0-9a-f-]+/)
  })

  test('does not navigate to another page when clicking on the other cells', async ({ page }) => {
    await page.getByRole('table').waitFor({ state: 'visible' })
    await page.getByTestId('searchArea').locator('input').fill(dataset.name)

    const datasetRow = page.getByRole('row').filter({ hasText: dataset.name })
    await datasetRow.waitFor({ state: 'visible' })

    // Columns: name, data pool, created by, last updated, status
    for (const index of [1, 3, 4]) {
      await datasetRow.getByRole('cell').nth(index).click()
      await expect(page.getByTestId('datasetsPage')).toBeVisible()
    }
  })
})
