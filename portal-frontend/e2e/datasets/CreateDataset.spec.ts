import { expect, test } from '@playwright/test'

import { getMockDatasetData } from '../../playwright/helpers/dataset/datasetFactory'
import { getSelectOptions } from '../utils/formUtils'

const MOCK_DATASET_1 = getMockDatasetData()

test.describe('Create Dataset Flow', async () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/datasets/create')
  })

  test('renders the page elements', async ({ page }) => {
    const formFieldsCount = 3
    await page.getByRole('form').waitFor({ state: 'visible' })

    const pageHeader = page.getByTestId('pageHeader')
    await expect(pageHeader).toBeVisible()
    await expect(pageHeader).toContainText('Create: New Dataset')

    await expect(page.getByTestId('cancelButton')).toBeVisible()
    await expect(page.getByTestId('confirmButton')).toBeVisible()
    await expect(page.getByTestId('editButton')).not.toBeVisible()
    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(formFieldsCount)
    // verify disabled state of form fields
    for (let i = 0; i < formFieldsCount; i++) {
      const field = fields.nth(i)
      await expect(field).toBeEnabled()
    }
    await expect(page.getByTestId('tagsInput')).toBeVisible()
  })

  test('cancel dataset creation navigates to datasets list', async ({ page }) => {
    await page.getByTestId('cancelButton').click()
    await expect(page.getByTestId('datasetsPage')).toBeVisible()
  })

  test('save button gets enabled after changing form values', async ({ page }) => {
    await expect(page.getByTestId('confirmButton')).toBeDisabled()
    await page.getByTestId('nameTextField').fill(MOCK_DATASET_1.name)
    await expect(page.getByTestId('confirmButton')).toBeEnabled()
  })

  test('creates new dataset with all the entered information', async ({ page }) => {
    // fill in form data
    let selectOptionText = 'Select Data Space...'
    const selectedOptions = await getSelectOptions(page, 'dataspace')
    if ((await selectedOptions.count()) > 0) {
      selectOptionText = (await selectedOptions.nth(1).textContent()) ?? 'Select Data Space...'
      await selectedOptions.nth(1).click()
    }

    await page.getByTestId('nameTextField').fill(MOCK_DATASET_1.name)
    await page.getByTestId('descriptionTextField').fill(MOCK_DATASET_1.description)
    await page.getByTestId('tagsInput').fill(MOCK_DATASET_1.tags[0])
    await page.getByTestId('tagsInput').press('Enter')
    await page.getByTestId('tagsInput').fill(MOCK_DATASET_1.tags[1])
    await page.getByTestId('tagsInput').press('Enter')
    const tags = page.getByTestId('tagsField').locator('span')

    await expect(tags).toHaveCount(2)

    await page.getByTestId('confirmButton').click()
    await page.waitForLoadState('networkidle')

    // verify redirect to new created user's details page in readonly view and check entered dataset information
    await expect(page.getByTestId('datasetPage')).toBeVisible()
    await expect(page.getByTestId('pageHeader')).toContainText(MOCK_DATASET_1.name)
    await expect(page.getByTestId('editButton')).toBeVisible()
    const fields = page.locator('[data-test-element="formField"]')
    for (const field of await fields.all()) {
      await expect(field).toBeDisabled()
    }
    await expect(page.getByTestId('tagsInput')).not.toBeVisible()
    await expect(page.getByTestId('dataspaceSelectTrigger')).toHaveText(selectOptionText)
    await expect(page.getByTestId('nameTextField')).toHaveValue(MOCK_DATASET_1.name)
    await expect(page.getByTestId('descriptionTextField')).toHaveValue(MOCK_DATASET_1.description)
    expect(await tags.allTextContents()).toEqual(MOCK_DATASET_1.tags)
  })

  test('new created dataset appears in datasets list', async ({ page }) => {
    // fill in form data
    const selectedOptions = await getSelectOptions(page, 'dataspace')
    if ((await selectedOptions.count()) > 0) {
      await selectedOptions.nth(1).click()
    }

    await page.getByTestId('nameTextField').fill(MOCK_DATASET_1.name)
    await page.getByTestId('descriptionTextField').fill(MOCK_DATASET_1.description)

    await page.getByTestId('confirmButton').click()
    await page.waitForLoadState('networkidle')

    // verify new created user appears in users list
    await page.getByTestId('sidebarMenuItem-ourData').click()
    await page.waitForLoadState('networkidle')
    await page.getByTestId('searchArea').locator('input').fill(MOCK_DATASET_1.name)

    const rows = page.getByRole('row')
    await expect(rows).toHaveCount(2)
    await expect(rows.filter({ hasText: MOCK_DATASET_1.name })).toBeVisible()
  })
})
