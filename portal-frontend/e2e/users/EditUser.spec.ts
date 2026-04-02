import { expect, test } from '@playwright/test'

import { Authority, User } from '@/types/users'

import { JSON_SERVER_HOST, JSON_SERVER_PORT } from '../../playwright.config'
import { createTestUser } from '../../playwright/createTestUser'
import { removeTestUser } from '../../playwright/removeTestUser'
import { pickSelectOption } from '../utils/formUtils'

const URL = `${JSON_SERVER_HOST}:${JSON_SERVER_PORT}`

const MOCK_AUTHORITIES = [
  {
    id: '1',
    title: 'authority1',
    departments: [
      {
        id: '1',
        title: 'department1',
      },
    ],
  },
]

// users tests are skipped because list view uses API and create and edit user use json-server

test.describe.skip('Edit User Page', async () => {
  let user: User
  test.beforeEach(async ({ page }) => {
    user = await createTestUser()
    await page.route(`${URL}/authorities`, async route => {
      const json: Authority[] = MOCK_AUTHORITIES
      await route.fulfill({ json })
    })
    await page.goto(`/users/${user.id}`)
  })

  test.afterEach(async () => {
    await removeTestUser(user.id)
  })

  test('renders the readonly view', async ({ page }) => {
    const formFieldsCount = 9
    const userDetailsForm = page.getByTestId('userDetailsForm')
    await page.getByTestId('userDetailsForm').waitFor({ state: 'visible' })
    await expect(userDetailsForm).toBeVisible()
    const subTabs = page.getByTestId('subTabs')
    await expect(subTabs).toBeVisible()

    const tabButtons = subTabs.locator('button')
    await expect(tabButtons).toHaveCount(5)
    for (const tab of await tabButtons.all()) {
      await expect(tab).toBeEnabled()
    }

    await expect(page.getByTestId('cancelButton')).toBeHidden()
    await expect(page.getByTestId('confirmButton')).toBeHidden()
    await expect(page.getByTestId('editButton')).toBeVisible()

    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(formFieldsCount)

    // verify if all form fields are disabled
    for (const field of await fields.all()) {
      await expect(field).toBeDisabled()
    }

    await expect(page.getByTestId('activeStatusLabel')).toBeVisible()
    await expect(page.getByTestId('activeStatus')).toContainText('Active')
  })

  test('enables user editing on edit button click', async ({ page }) => {
    const formFieldsCount = 10

    await page.getByTestId('editButton').click()
    await expect(page.getByTestId('cancelButton')).toBeVisible()
    await expect(page.getByTestId('confirmButton')).toBeVisible()
    await expect(page.getByTestId('activeStatusLabel')).toBeHidden()

    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(formFieldsCount)
    // verify if all form fields but the id field are enabled
    for (let i = 0; i < formFieldsCount; i++) {
      const field = fields.nth(i)
      if (i === 0) {
        await expect(field).toBeDisabled()
      } else {
        await expect(field).toBeEnabled()
      }
    }
  })

  test('save button gets enabled after changing form values', async ({ page }) => {
    await page.getByTestId('editButton').click()
    await expect(page.getByTestId('confirmButton')).toBeDisabled()
    await page.getByTestId('firstNameTextField').fill('Test first name')
    await expect(page.getByTestId('confirmButton')).toBeEnabled()
  })

  test('form validation highlights incorrectly filled in fields and shows error messages', async ({ page }) => {
    await page.getByTestId('editButton').click()
    await page.getByTestId('firstNameTextField').fill('')
    await page.getByTestId('confirmButton').click()

    await expect(page.getByTestId('firstNameTextField')).toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByTestId('firstNameFormMessage')).toBeVisible()

    await page.getByTestId('firstNameTextField').fill('Test Name')
    await expect(page.getByTestId('firstNameTextField')).toHaveAttribute('aria-invalid', 'false')
    await expect(page.getByTestId('firstNameFormMessage')).toBeHidden()
  })

  test('cancel user editing navigates to users list', async ({ page }) => {
    await page.getByTestId('editButton').click()
    await page.getByTestId('cancelButton').click()
    await expect(page.getByTestId('usersPage')).toBeVisible()
  })

  test('edits user details', async ({ page }) => {
    await page.getByTestId('editButton').click()

    // fill in form data
    await pickSelectOption(page, 'title', 1)
    await page.getByTestId('firstNameTextField').fill(`edited ${user.firstName}`)
    await page.getByTestId('lastNameTextField').fill(`edited ${user.lastName}`)
    await pickSelectOption(page, 'department', 'Placeholder')
    await page.getByTestId('positionDescriptionTextArea').fill('edited description')
    await page.getByTestId('activeSwitch').click()

    await page.getByTestId('confirmButton').click()
    await page.waitForLoadState('networkidle')
    await page.getByTestId('editButton').waitFor({ state: 'visible' })

    // Verify if edited fields show correct content
    // names fields have to be checked like this since the name is too long for the field
    const firstName = (await page.getByTestId('firstNameTextField').textContent())?.trim() ?? ''
    expect(`edited ${user.firstName}`).toContain(firstName)
    const lastName = (await page.getByTestId('lastNameTextField').textContent())?.trim() ?? ''
    expect(`edited ${user.lastName}`).toContain(lastName)
    await expect(page.getByTestId('pageHeader')).toContainText(`edited ${user.firstName} edited ${user.lastName}`)
    await expect(page.getByTestId('departmentSelectTrigger')).toContainText('Select department...')
    await expect(page.getByTestId('activeStatus')).toContainText('Inactive')

    // verify if page gets set back to readonly view
    await expect(page.getByTestId('editButton')).toBeVisible()
    const fields = page.locator('[data-test-element="formField"]')
    for (const field of await fields.all()) {
      await expect(field).toBeDisabled()
    }
  })
})
