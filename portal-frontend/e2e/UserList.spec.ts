import { expect, test } from '@playwright/test'

import { Authority } from '@/types/users'
import { pickSelectOption } from './utils/formUtils'
import {
  E2E_MOCK_EMAIL,
  E2E_MOCK_FIRSTNAME,
  E2E_MOCK_LASTNAME,
  TEST_EMAIL,
  TEST_FIRSTNAME,
  TEST_LASTNAME,
} from '../playwright.config'
import { removeTestUsers } from '../playwright/removeTestUsers'
import { getMockUserData } from '../playwright/helpers/userFactory'
import { createTestUser } from '../playwright/createTestUser'

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

test.describe('User List Page', async () => {
  test.beforeEach(async ({ page }) => {
    await removeTestUsers()
    await page.goto('/users')
  })

  test('renders the page elements', async ({ page }) => {
    const pageHeader = page.getByTestId('pageHeader')
    await expect(pageHeader).toBeVisible()
    await expect(pageHeader).toContainText('Overview: Users')

    await expect(page.getByTestId('addUserButton')).toBeVisible()

    await expect(page.getByTestId('usersTable')).toBeVisible()

    const searchArea = page.getByTestId('searchArea')
    await expect(searchArea).toBeVisible()
    searchArea.locator('input').fill(E2E_MOCK_FIRSTNAME)

    const rows = page.getByRole('row')
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(1)).toContainText('No results found.')
  })

  test('navigates to create user page', async ({ page }) => {
    const addUserButton = page.getByTestId('addUserButton')
    await addUserButton.click()
    const createUserPage = page.getByTestId('createUserPage')
    await expect(createUserPage).toBeVisible()
  })

  test('creates new user', async ({ page }) => {
    await page.getByTestId('addUserButton').click()

    // FILL IN FORM DATA
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

  test('edits existing user', async ({ page }) => {
    const newUser = await createTestUser()
    const rows = page.getByRole('row')

    await rows.filter({ hasText: newUser.displayName }).click()
  })
})
