import { expect, test } from '@playwright/test'

import { DatasetResponse } from '@/types/datasets'

import { createTestDataset } from '../../playwright/helpers/dataset/createTestDataset'
import { removeTestDataset } from '../../playwright/helpers/dataset/removeTestDataset'
import { pickSelectOption } from '../utils/formUtils'

const COMPLETION_STEPS = [
  'Metadata',
  'Access permissions',
  'Data',
  'Distribution',
  'Usage Permissions',
  'Applications',
  'Publications',
]

test.describe('Edit Dataset Page', async () => {
  let dataset: DatasetResponse
  test.beforeEach(async ({ page }) => {
    dataset = await createTestDataset()
    await page.goto(`/datasets/${dataset.id}`)
  })

  test.afterEach(async () => {
    await removeTestDataset(dataset.id)
  })

  test('renders the page elements', async ({ page }) => {
    const formFieldsCount = 3
    const completionStepsCount = 7
    await page.getByRole('form').waitFor({ state: 'visible' })

    const pageHeader = page.getByTestId('pageHeader')
    await expect(pageHeader).toBeVisible()
    await expect(pageHeader).toContainText(dataset.name)

    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(formFieldsCount)
    await expect(page.getByTestId('tagsField')).toBeVisible()

    const completionSteps = page.getByTestId('completionStep')
    await expect(completionSteps).toHaveCount(completionStepsCount)
    for (let i = 0; i < (await completionSteps.count()); i++) {
      await expect(completionSteps.nth(i).locator('h3')).toHaveText(COMPLETION_STEPS[i])
      const buttonsLinks = completionSteps.nth(i).locator('a')
      if (i === 3) await expect(buttonsLinks).toHaveCount(2)
      else await expect(buttonsLinks).toHaveCount(1)
    }
  })

  test('renders the readonly view', async ({ page }) => {
    const formFieldsCount = 3
    await page.getByRole('form').waitFor({ state: 'visible' })

    await expect(page.getByTestId('cancelButton')).toBeHidden()
    await expect(page.getByTestId('confirmButton')).toBeHidden()
    await expect(page.getByTestId('editButton')).toBeVisible()

    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(formFieldsCount)

    // verify if all form fields are disabled
    for (const field of await fields.all()) {
      await expect(field).toBeDisabled()
    }
    await expect(page.getByTestId('tagsInput')).not.toBeVisible()
  })

  test('enables dataset editing on edit button click', async ({ page }) => {
    const formFieldsCount = 3

    await page.getByTestId('editButton').click()
    await expect(page.getByTestId('cancelButton')).toBeVisible()
    await expect(page.getByTestId('confirmButton')).toBeVisible()

    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(formFieldsCount)
    // verify if all form fields but the id field are enabled
    for (const field of await fields.all()) {
      await expect(field).toBeEnabled()
    }
    await expect(page.getByTestId('tagsInput')).toBeVisible()
  })

  test('cancel dataset editing navigates to users list', async ({ page }) => {
    await page.getByTestId('editButton').click()
    await page.getByTestId('cancelButton').click()
    await expect(page.getByTestId('datasetsPage')).toBeVisible()
  })

  test('edits dataset details', async ({ page }) => {
    await page.getByTestId('editButton').click()

    // edit form data
    await pickSelectOption(page, 'dataspace', 'Placeholder')
    await page.getByTestId('nameTextField').fill(`edited ${dataset.name}`)
    await page.getByTestId('descriptionTextField').fill(`edited ${dataset.description}`)
    const tagsField = page.getByTestId('tagsField')

    await tagsField.locator('span button').nth(1).click()

    await page.getByTestId('confirmButton').click()
    await page.waitForLoadState('networkidle')
    await page.getByTestId('editButton').waitFor({ state: 'visible' })

    // Verify if edited fields show correct content
    // names fields have to be checked like this since the name is too long for the field
    await expect(page.getByTestId('nameTextField')).toHaveValue(`edited ${dataset.name}`)
    await expect(page.getByTestId('descriptionTextField')).toHaveValue(`edited ${dataset.description}`)
    await expect(page.getByTestId('pageHeader')).toContainText(`edited ${dataset.name}`)
    expect(await tagsField.locator('span').allTextContents()).toEqual([dataset.tags[0]])

    // verify if page gets set back to readonly view
    await expect(page.getByTestId('editButton')).toBeVisible()
    const fields = page.locator('[data-test-element="formField"]')
    for (const field of await fields.all()) {
      await expect(field).toBeDisabled()
    }
    await expect(page.getByTestId('tagsInput')).not.toBeVisible()
  })
})
