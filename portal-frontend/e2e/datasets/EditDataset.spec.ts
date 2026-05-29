import { expect, test } from '@playwright/test'

import { Dataset } from '@/types/datasets'

import { createTestDataset } from '../../playwright/helpers/dataset/createTestDataset'
import { removeTestDataset } from '../../playwright/helpers/dataset/removeTestDataset'

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
  let dataset: Dataset
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
  })

  test('cancel dataset editing navigates to users list', async ({ page }) => {
    await page.getByTestId('editButton').click()
    await page.getByTestId('cancelButton').click()
    await expect(page.getByTestId('datasetsPage')).toBeVisible()
  })

  test('edits dataset details', async ({ page }) => {
    await page.getByTestId('editButton').click()

    // edit form data
    await page.getByTestId('nameTextField').fill(`edited ${dataset.name}`)
    await page.getByTestId('descriptionTextField').fill(`edited ${dataset.description}`)

    await page.getByTestId('confirmButton').click()
    await page.waitForLoadState('networkidle')
    await page.getByTestId('editButton').waitFor({ state: 'visible' })

    // Verify if edited fields show correct content
    // names fields have to be checked like this since the name is too long for the field
    await expect(page.getByTestId('nameTextField')).toHaveValue(`edited ${dataset.name}`)
    await expect(page.getByTestId('descriptionTextField')).toHaveValue(`edited ${dataset.description}`)
    await expect(page.getByTestId('pageHeader')).toContainText(`edited ${dataset.name}`)

    // verify if page gets set back to readonly view
    await expect(page.getByTestId('editButton')).toBeVisible()
    const fields = page.locator('[data-test-element="formField"]')
    for (const field of await fields.all()) {
      await expect(field).toBeDisabled()
    }
  })
})

test.describe('Edit Dataset Page completion steps', () => {
  test.describe.configure({ mode: 'serial' })

  let dataset: Dataset
  test.beforeEach(async ({ page }) => {
    dataset = await createTestDataset()
    await page.goto(`/datasets/${dataset.id}`)
  })

  test.afterEach(async () => {
    await removeTestDataset(dataset.id)
  })

  test('button links navigate to completion steps', async ({ page }) => {
    const completionSteps = page.getByTestId('completionStep')

    for (const step of await completionSteps.all()) {
      const stepTitle = await step.locator('h3').textContent()
      const links = step.locator('a')
      for (const link of await links.all()) {
        await link.click()
        await expect(page.getByTestId('pageHeader')).toHaveText(stepTitle ?? '')
        if (stepTitle !== 'Data') {
          await page.getByRole('link', { name: 'Back' }).waitFor({ state: 'visible' })
        }
        await page.goto(`/datasets/${dataset.id}`)
        await page.waitForLoadState('networkidle')
      }
    }
  })
})
