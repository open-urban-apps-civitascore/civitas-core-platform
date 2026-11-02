import { expect, test } from '@playwright/test'

import { getMockDatasetData } from '../../playwright/helpers/dataset/datasetFactory'
import { removeTestDatasetsByName } from '../../playwright/helpers/dataset/removeTestDataset'

const MOCK_DATASET = getMockDatasetData()

test.describe('Create Dataset Flow', async () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/datasets/create')
  })

  test.afterEach(async () => {
    await removeTestDatasetsByName(MOCK_DATASET.name)
  })

  test('renders the page elements', async ({ page }) => {
    const formFieldsCount = 3
    await page.getByRole('form').waitFor({ state: 'visible' })

    const pageHeader = page.getByTestId('pageHeader')
    await expect(pageHeader).toBeVisible()
    await expect(pageHeader).toContainText('New Dataset')

    await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Save and continue' })).toBeVisible()
    await expect(page.getByTestId('editButton')).not.toBeVisible()
    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(formFieldsCount)
    for (let i = 0; i < formFieldsCount; i++) {
      await expect(fields.nth(i)).toBeEnabled()
    }
    await expect(page.getByTestId('completionStep')).not.toBeVisible()
  })

  test('cancel dataset creation navigates to datasets list', async ({ page }) => {
    await page.getByRole('button', { name: 'Cancel' }).click()
    await expect(page.getByTestId('datasetsPage')).toBeVisible()
  })

  test('save button gets enabled after changing form values', async ({ page }) => {
    const saveButton = page.getByRole('button', { name: 'Save and continue' })
    await expect(saveButton).toBeDisabled()
    await page.getByTestId('nameTextField').fill(MOCK_DATASET.name)
    await expect(saveButton).toBeEnabled()
  })

  test('creates new dataset with all the entered information', async ({ page }) => {
    await page.getByTestId('nameTextField').fill(MOCK_DATASET.name)
    await page.getByTestId('descriptionTextArea').fill(MOCK_DATASET.description)

    await page.getByRole('button', { name: 'Save and continue' }).click()

    // The new dataset opens in its edit mode
    await page.waitForURL(/\/datasets\/[0-9a-f-]+\?mode=edit/)
    await expect(page.getByTestId('datasetPage')).toBeVisible()
    await expect(page.getByTestId('pageHeader')).toContainText(MOCK_DATASET.name)
    await expect(page.getByTestId('nameTextField')).toHaveValue(MOCK_DATASET.name)
    await expect(page.getByTestId('descriptionTextArea')).toHaveValue(MOCK_DATASET.description)
  })

  test('new created dataset appears in datasets list', async ({ page }) => {
    await page.getByTestId('nameTextField').fill(MOCK_DATASET.name)
    await page.getByTestId('descriptionTextArea').fill(MOCK_DATASET.description)

    await page.getByRole('button', { name: 'Save and continue' }).click()
    await page.waitForURL(/\/datasets\/[0-9a-f-]+\?mode=edit/)

    await page.goto('/datasets')
    await page.waitForLoadState('networkidle')
    await page.getByTestId('searchArea').locator('input').fill(MOCK_DATASET.name)

    const rows = page.getByRole('row')
    await expect(rows).toHaveCount(2)
    await expect(rows.filter({ hasText: MOCK_DATASET.name })).toBeVisible()
  })
})
