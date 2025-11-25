import { expect, test } from '@playwright/test'

import { Authority, UserResponse } from '@/types/users'

import { createTestUser } from '../../playwright/createTestUser'
import { getMockUserData } from '../../playwright/helpers/userFactory'
import { removeTestUser } from '../../playwright/removeTestUser'
import { pickSelectOption } from '../utils/formUtils'

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

test.describe('Edit User Page', async () => {
  let user: UserResponse
  test.beforeEach(async ({ page }) => {
    user = await createTestUser()
    await page.route('http://localhost:3001/authorities', async route => {
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
    const secondaryTabs = page.getByTestId('secondaryTabs')
    await expect(secondaryTabs).toBeVisible()

    const tabButtons = secondaryTabs.locator('button')
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

  test('edits user details', async ({ page }) => {
    await page.getByTestId('editButton').click()

    // fill in form data
    await pickSelectOption(page, 'title', 1)
    await page.getByTestId('firstNameTextField').fill(`edited ${MOCK_USER_1.firstName}`)
    await page.getByTestId('lastNameTextField').fill(`edited ${MOCK_USER_1.lastName}`)
    await pickSelectOption(page, 'department', 'Placeholder')
    await page.getByTestId('positionDescriptionTextArea').fill('edited description')
    await page.getByTestId('activeSwitch').click()

    await page.getByTestId('confirmButton').click()
    await page.waitForLoadState('networkidle')

    await expect(page.getByTestId('userDetailsPage')).toBeVisible()
    await expect(page.getByTestId('pageHeader')).toContainText(
      `edited ${MOCK_USER_1.firstName} edited ${MOCK_USER_1.lastName}`,
    )

    await expect(page.getByTestId('departmentSelectTrigger')).toContainText('Select department...')
    await expect(page.getByTestId('activeStatus')).toContainText('Inactive')
  })

  test('cancel user editing navigates to users list', async ({ page }) => {
    await page.getByTestId('editButton').click()
    await page.getByTestId('cancelButton').click()
    await expect(page.getByTestId('usersPage')).toBeVisible()
  })
})
