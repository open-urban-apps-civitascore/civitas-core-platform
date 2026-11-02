import { expect, test } from '@playwright/test'

import { createTestDataset, TestDataset } from '../../playwright/helpers/dataset/createTestDataset'
import { removeTestDataset } from '../../playwright/helpers/dataset/removeTestDataset'

const FORM_FIELDS_COUNT = 3 // name, data pool, description

test.describe('Edit Dataset Page', async () => {
  let dataset: TestDataset
  test.beforeEach(async ({ page }) => {
    dataset = await createTestDataset()
    await page.goto(`/datasets/${dataset.id}`)
    await page.getByRole('form').first().waitFor({ state: 'visible' })
  })

  test.afterEach(async () => {
    await removeTestDataset(dataset.id)
  })

  test('renders the page elements', async ({ page }) => {
    const pageHeader = page.getByTestId('pageHeader')
    await expect(pageHeader).toBeVisible()
    await expect(pageHeader).toContainText(dataset.name)

    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(FORM_FIELDS_COUNT)

    const completionSteps = page.getByTestId('completionStep')
    for (const title of ['Data flow', 'Access']) {
      await expect(completionSteps.filter({ has: page.getByRole('heading', { name: title, level: 3 }) })).toHaveCount(1)
    }
  })

  test('renders the readonly view', async ({ page }) => {
    await expect(page.getByTestId('cancelButton')).toBeHidden()
    await expect(page.getByTestId('confirmButton')).toBeHidden()
    await expect(page.getByTestId('editButton')).toBeVisible()

    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(FORM_FIELDS_COUNT)
    for (const field of await fields.all()) {
      await expect(field).toBeDisabled()
    }
  })

  test('enables dataset editing on edit button click', async ({ page }) => {
    await page.getByTestId('editButton').click()
    await expect(page.getByTestId('cancelButton')).toBeVisible()
    await expect(page.getByTestId('confirmButton')).toBeVisible()

    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(FORM_FIELDS_COUNT)
    for (const field of await fields.all()) {
      await expect(field).toBeEnabled()
    }
  })

  test('exiting the edit mode without changes returns to the readonly view', async ({ page }) => {
    await page.getByTestId('editButton').click()
    await page.getByTestId('cancelButton').click()
    await expect(page.getByTestId('editButton')).toBeVisible()
    await expect(page.getByTestId('confirmButton')).toBeHidden()
  })

  test('edits dataset details', async ({ page }) => {
    const editedName = `edited ${dataset.name}`
    const editedDescription = `edited ${dataset.description}`

    await page.getByTestId('editButton').click()
    await page.getByTestId('nameTextField').fill(editedName)
    await page.getByTestId('descriptionTextArea').fill(editedDescription)
    await page.getByTestId('confirmButton').click()

    await expect(page.getByTestId('pageHeader')).toContainText(editedName)
    await expect(page.getByTestId('nameTextField')).toHaveValue(editedName)
    await expect(page.getByTestId('descriptionTextArea')).toHaveValue(editedDescription)

    // Saving may leave the page in the edit mode; the page is read only once it is exited
    const exitButton = page.getByTestId('cancelButton')
    if (await exitButton.isVisible()) await exitButton.click()
    await expect(page.getByTestId('editButton')).toBeVisible()
    for (const field of await page.locator('[data-test-element="formField"]').all()) {
      await expect(field).toBeDisabled()
    }
  })
})

test.describe('Edit Dataset Page completion steps', () => {
  test.describe.configure({ mode: 'serial' })

  let dataset: TestDataset
  test.beforeEach(async ({ page }) => {
    dataset = await createTestDataset()
    await page.goto(`/datasets/${dataset.id}`)
    await page.getByRole('form').first().waitFor({ state: 'visible' })
  })

  test.afterEach(async () => {
    await removeTestDataset(dataset.id)
  })

  test('button links of the completion steps leave the overview page', async ({ page }) => {
    const links = page.getByTestId('completionStep').locator('a')
    const linkCount = await links.count()
    expect(linkCount).toBeGreaterThan(0)

    for (let i = 0; i < linkCount; i++) {
      await page.getByTestId('completionStep').locator('a').nth(i).click()
      await expect(page).not.toHaveURL(new RegExp(`/datasets/${dataset.id}$`))
      await page.goto(`/datasets/${dataset.id}`)
      await page.getByRole('form').first().waitFor({ state: 'visible' })
    }
  })
})
