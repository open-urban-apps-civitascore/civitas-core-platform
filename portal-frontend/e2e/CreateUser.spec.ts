import { expect, test } from '@playwright/test'

import { getMockUserData } from '../playwright/helpers/userFactory'
import { removeTestUsers } from '../playwright/removeTestUsers'
import { pickSelectOption } from './utils/formUtils'
import { Authority } from '@/types/users'

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
    await removeTestUsers()
    await page.route('http://localhost:3001/authorities', async route => {
      const json: Authority[] = MOCK_AUTHORITIES
      await route.fulfill({ json })
    })
    await page.goto('/users/create')
  })

  test('renders the page elements', async ({ page }) => {
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
    await expect(fields).toHaveCount(10)
    // verify disabled state of form fields
    for (let i = 0; i < 10; i++) {
      const field = fields.nth(i)
      if (i === 0) {
        await expect(field).toBeDisabled()
      } else {
        await expect(field).not.toBeDisabled()
      }
    }
  })

  test('creates new user', async ({ page }) => {
    // fill in form data
    await pickSelectOption(page, 'title', 1)
    await page.getByTestId('firstNameTextField').fill(MOCK_USER_1.firstName)
    await page.getByTestId('lastNameTextField').fill(MOCK_USER_1.lastName)
    await page.getByTestId('emailTextField').fill(MOCK_USER_1.email)
    await page.getByTestId('phoneTextField').fill(MOCK_USER_1.phone)
    await pickSelectOption(page, 'authority')
    await pickSelectOption(page, 'department')
    await page.getByTestId('positionDescriptionTextArea').fill(MOCK_USER_1.positionDescription || '')
    await page.getByTestId('activeSwitch').click()

    await page.getByTestId('confirmButton').click()

    // verify redirect zu new users details page
    await expect(page).toHaveURL(/\/users\/.+/)
    await expect(page.getByTestId('editUserPage')).toBeVisible()
    await expect(page.getByTestId('pageHeader')).toContainText(MOCK_USER_1.displayName)

    // verify new created user appears in users list
    await page.goto('/users')
    await page.getByTestId('searchArea').locator('input').fill(MOCK_USER_1.firstName)
    const rows = page.getByRole('row')
    await expect(rows).toHaveCount(2)
    await expect(rows.filter({ hasText: MOCK_USER_1.displayName })).toBeVisible()
  })
})
