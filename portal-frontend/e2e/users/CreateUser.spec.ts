import { expect, test } from '@playwright/test'

import { Authority } from '@/types/users'

import { JSON_SERVER_HOST, JSON_SERVER_PORT } from '../../playwright.config'
import { getMockUserData } from '../../playwright/helpers/userFactory'
import { pickSelectOption } from '../utils/formUtils'

const URL = `${JSON_SERVER_HOST}:${JSON_SERVER_PORT}`

const MOCK_USER_1 = getMockUserData()

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

test.describe('Create User Flow', async () => {
  test.beforeEach(async ({ page }) => {
    await page.route(`${URL}/authorities`, async route => {
      const json: Authority[] = MOCK_AUTHORITIES
      await route.fulfill({ json })
    })
    await page.goto('/users/create')
  })

  test('renders the page elements', async ({ page }) => {
    const formFieldsCount = 10
    await page.getByTestId('userDetailsForm').waitFor({ state: 'visible' })

    const pageHeader = page.getByTestId('pageHeader')
    await expect(pageHeader).toBeVisible()
    await expect(pageHeader).toContainText('Create User')

    await expect(page.getByTestId('userDetailsForm')).toBeVisible()
    const secondaryTabs = page.getByTestId('secondaryTabs')
    await expect(secondaryTabs).toBeVisible()
    await expect(secondaryTabs.locator('button')).toHaveCount(5)
    await expect(secondaryTabs.locator('button').nth(1)).toBeDisabled()

    await expect(page.getByTestId('userDetailsForm')).toBeVisible()
    await expect(page.getByTestId('cancelButton')).toBeVisible()
    await expect(page.getByTestId('confirmButton')).toBeVisible()
    await expect(page.getByTestId('editButton')).not.toBeVisible()
    const fields = page.locator('[data-test-element="formField"]')
    await expect(fields).toHaveCount(formFieldsCount)
    // verify disabled state of form fields
    for (let i = 0; i < formFieldsCount; i++) {
      const field = fields.nth(i)
      if (i === 0) {
        await expect(field).toBeDisabled()
      } else {
        await expect(field).toBeEnabled()
      }
    }
  })

  test('cancel user creation navigates to users list', async ({ page }) => {
    await page.getByTestId('cancelButton').click()
    await expect(page.getByTestId('usersPage')).toBeVisible()
  })

  test('save button gets enabled after changing form values', async ({ page }) => {
    await expect(page.getByTestId('confirmButton')).toBeDisabled()
    await page.getByTestId('firstNameTextField').fill(MOCK_USER_1.firstName)
    await expect(page.getByTestId('confirmButton')).toBeEnabled()
  })

  test('form validation highlights incorrectly filled in fields and shows error messages', async ({ page }) => {
    await pickSelectOption(page, 'title', 1)
    await page.getByTestId('confirmButton').click()
    await expect(page.getByTestId('firstNameTextField')).toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByTestId('lastNameTextField')).toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByTestId('emailTextField')).toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByTestId('firstNameFormMessage')).toBeVisible()
    await expect(page.getByTestId('lastNameFormMessage')).toBeVisible()
    await expect(page.getByTestId('emailFormMessage')).toBeVisible()

    await page.getByTestId('firstNameTextField').fill('Test Name')
    await expect(page.getByTestId('firstNameTextField')).toHaveAttribute('aria-invalid', 'false')
    await expect(page.getByTestId('firstNameFormMessage')).toBeHidden()
  })

  test('creates new user', async ({ page }) => {
    // fill in form data
    await expect(page.getByTestId('confirmButton')).toBeDisabled()

    await pickSelectOption(page, 'title', 1)
    await expect(page.getByTestId('confirmButton')).toBeEnabled()

    await page.getByTestId('firstNameTextField').fill(MOCK_USER_1.firstName)
    await page.getByTestId('lastNameTextField').fill(MOCK_USER_1.lastName)
    await page.getByTestId('emailTextField').fill(MOCK_USER_1.email)
    await page.getByTestId('phoneTextField').fill(MOCK_USER_1.phone)
    await pickSelectOption(page, 'authority')
    await pickSelectOption(page, 'department')
    await page.getByTestId('positionDescriptionTextArea').fill(MOCK_USER_1.positionDescription || '')
    await page.getByTestId('activeSwitch').click()

    await page.getByTestId('confirmButton').click()
    await page.waitForLoadState('networkidle')

    // verify redirect to new created user's details page in readonly view
    await expect(page.getByTestId('userDetailsPage')).toBeVisible()
    await expect(page.getByTestId('pageHeader')).toContainText(MOCK_USER_1.displayName)
    await expect(page.getByTestId('editButton')).toBeVisible()
    const fields = page.locator('[data-test-element="formField"]')
    for (const field of await fields.all()) {
      await expect(field).toBeDisabled()
    }

    // verify new created user appears in users list
    await page.getByTestId('sidebarMenuItem-users').click()
    await page.waitForLoadState('networkidle')
    await page.getByTestId('searchArea').locator('input').fill(MOCK_USER_1.firstName)

    const rows = page.getByRole('row')
    await expect(rows).toHaveCount(2)
    await expect(rows.filter({ hasText: MOCK_USER_1.displayName })).toBeVisible()
  })
})
