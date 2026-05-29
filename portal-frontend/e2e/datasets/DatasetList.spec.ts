import { expect, test } from '@playwright/test'

import { Dataset } from '@/types/datasets'

import { createTestDataset } from '../../playwright/helpers/dataset/createTestDataset'
import { removeTestDataset } from '../../playwright/helpers/dataset/removeTestDataset'

test.describe('Dataset List', async () => {
  let dataset: Dataset

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
    await expect(page.getByRole('button', { name: 'New Dataset' })).toBeVisible()

    await expect(page.getByTestId('datasetsTable')).toBeVisible()

    const searchArea = page.getByTestId('searchArea')
    await expect(searchArea).toBeVisible()
    searchArea.locator('input').fill(dataset.name)

    const rows = page.getByRole('row')
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(1)).toContainText(dataset.name)

    searchArea.locator('input').fill(crypto.randomUUID())
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(1)).toContainText('No results found.')
  })

  test('navigates to create dataset overview page', async ({ page }) => {
    const addDatasetButton = page.getByRole('button', { name: 'New Dataset' })
    await addDatasetButton.click()
    await page.waitForLoadState('networkidle')
    const createDatasetPage = page.getByTestId('createDatasetPage')
    await expect(createDatasetPage).toBeVisible()
  })

  test('navigates to dataset overview page when clicking on dataset name', async ({ page }) => {
    await page.getByRole('table').waitFor({ state: 'visible' })
    await page.getByTestId('searchArea').locator('input').fill(dataset.name)

    const datasetRow = page.getByRole('row').filter({ hasText: dataset.name })
    await datasetRow.waitFor({ state: 'visible' })
    const datasetCell = datasetRow.getByRole('cell', { name: dataset.name })
    await datasetCell.click()
    await expect(page.getByTestId('datasetPage')).toBeVisible()
    await expect(page.getByTestId('pageHeader')).toContainText(dataset.name)
  })

  test('navigates to user details when clicking on the contact', async ({ page }) => {
    await page.getByRole('table').waitFor({ state: 'visible' })
    await page.getByTestId('searchArea').locator('input').fill(dataset.name)

    const datasetRow = page.getByRole('row').filter({ hasText: dataset.name })
    await datasetRow.waitFor({ state: 'visible' })
    const contactCell = datasetRow.getByRole('cell', {
      name: `${dataset.contact?.firstName} ${dataset.contact?.lastName}`,
    })
    await contactCell.click()
    await expect(page.getByTestId('userDetailsPage')).toBeVisible()
  })

  test('does not navigate to another page when clicking on the other cells', async ({ page }) => {
    await page.getByRole('table').waitFor({ state: 'visible' })
    await page.getByTestId('searchArea').locator('input').fill(dataset.name)

    const datasetRow = page.getByRole('row').filter({ hasText: dataset.name })
    await datasetRow.waitFor({ state: 'visible' })

    const updatedCell = datasetRow.getByRole('cell').nth(3)
    await updatedCell.click()
    await expect(page.getByTestId('datasetsPage')).toBeVisible()

    const accessCell = datasetRow.getByRole('cell').nth(4)
    await accessCell.click()
    await expect(page.getByTestId('datasetsPage')).toBeVisible()

    const statusCell = datasetRow.getByRole('cell').nth(5)
    await statusCell.click()
    await expect(page.getByTestId('datasetsPage')).toBeVisible()
  })
})
